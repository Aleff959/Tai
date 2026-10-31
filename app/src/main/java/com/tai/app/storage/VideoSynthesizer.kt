package com.tai.app.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.*
import android.view.Surface
import com.tai.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class VideoSynthesizer(private val context: Context) {

    suspend fun createVideoFromImageAndAudio(
        imageFile: File,
        audioFile: File,
        outputFile: File,
        audioDurationMs: Long,
        onProgress: (Int) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: throw Exception("Não foi possível carregar a imagem para o vídeo")

        val durationUs = (if (audioDurationMs > 0) audioDurationMs else 15000L) * 1000L
        val width = 1080
        val height = 1920
        val fps = 24
        val frameDurationUs = 1000000L / fps
        val totalFrames = (durationUs / frameDurationUs).toInt().coerceAtLeast(1)

        val tempVideoFile = File(context.cacheDir, "temp_synth_video.mp4")
        val tempAacAudioFile = File(context.cacheDir, "temp_synth_audio.mp4")
        if (tempVideoFile.exists()) tempVideoFile.delete()
        if (tempAacAudioFile.exists()) tempAacAudioFile.delete()

        AppLogger.i("Synthesizer", "Iniciando codificação de vídeo: $totalFrames frames, $fps fps")

        // 1. Configurar MediaCodec para codificar os frames de vídeo H.264
        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 3_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = videoEncoder.createInputSurface()
        videoEncoder.start()

        var muxer: MediaMuxer? = null
        var videoTrackIndex = -1
        var muxerStarted = false

        try {
            muxer = MediaMuxer(tempVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            var presentationTimeUs = 0L
            var frameIndex = 0

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val destRect = calculateCenterInsideRect(bitmap.width, bitmap.height, width, height)

            while (frameIndex < totalFrames) {
                val canvas: Canvas = inputSurface.lockCanvas(null)
                canvas.drawColor(Color.BLACK)
                val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(bitmap, srcRect, destRect, paint)
                inputSurface.unlockCanvasAndPost(canvas)

                drainEncoder(videoEncoder, muxer, bufferInfo, false) { trackIdx, started ->
                    videoTrackIndex = trackIdx
                    muxerStarted = started
                }

                presentationTimeUs += frameDurationUs
                frameIndex++

                val progress = ((frameIndex.toFloat() / totalFrames) * 45).toInt()
                onProgress(progress)
            }

            videoEncoder.signalEndOfInputStream()
            drainEncoder(videoEncoder, muxer, bufferInfo, true) { trackIdx, started ->
                videoTrackIndex = trackIdx
                muxerStarted = started
            }

        } finally {
            try {
                videoEncoder.stop()
                videoEncoder.release()
                inputSurface.release()
                if (muxerStarted && muxer != null) {
                    muxer.stop()
                    muxer.release()
                }
            } catch (e: Exception) {
                AppLogger.e("Synthesizer", "Erro ao finalizar encoder de vídeo", e)
            }
        }

        onProgress(50)

        // 2. Transcodificar o áudio para AAC puro (compatível com MediaMuxer MP4)
        AppLogger.i("Synthesizer", "Transcodificando áudio para AAC...")
        val audioTranscoded = transcodeAudioToAac(audioFile, tempAacAudioFile, durationUs)

        onProgress(75)

        // 3. Mescla o vídeo gerado com o áudio AAC no arquivo final
        AppLogger.i("Synthesizer", "Mesclando vídeo e áudio final no MP4...")
        val effectiveAudioFile = if (audioTranscoded && tempAacAudioFile.exists()) tempAacAudioFile else audioFile
        muxVideoAndAudio(tempVideoFile, effectiveAudioFile, outputFile, durationUs)

        onProgress(100)

        tempVideoFile.delete()
        tempAacAudioFile.delete()

        AppLogger.i("Synthesizer", "Vídeo gerado com sucesso: ${outputFile.length()} bytes")
        outputFile
    }

    private fun transcodeAudioToAac(inputFile: File, outputFile: File, maxDurationUs: Long): Boolean {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                AppLogger.e("AudioTranscoder", "Nenhuma faixa de áudio encontrada no arquivo baixado")
                return false
            }

            extractor.selectTrack(audioTrackIndex)

            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            AppLogger.i("AudioTranscoder", "Decodificando $inputMime ($sampleRate Hz, $channelCount canais) -> AAC")

            // Configura Decoder
            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // Configura Encoder AAC
            val outputFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var aacTrackIndex = -1

            val decBufferInfo = MediaCodec.BufferInfo()
            val encBufferInfo = MediaCodec.BufferInfo()

            var extractorDone = false
            var decoderDone = false
            var encoderDone = false

            val timeoutUs = 5000L

            while (!encoderDone) {
                // 1. Extrai dados do arquivo para o Decoder
                if (!extractorDone) {
                    val inputBufIdx = decoder.dequeueInputBuffer(timeoutUs)
                    if (inputBufIdx >= 0) {
                        val inputBuf = decoder.getInputBuffer(inputBufIdx) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            decoder.queueInputBuffer(inputBufIdx, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. Coleta PCM decodificado e envia para o Encoder
                if (!decoderDone) {
                    val decOutIdx = decoder.dequeueOutputBuffer(decBufferInfo, timeoutUs)
                    if (decOutIdx >= 0) {
                        val pcmBuf = decoder.getOutputBuffer(decOutIdx)
                        if (pcmBuf != null && decBufferInfo.size > 0) {
                            val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                            if (encInIdx >= 0) {
                                val encInBuf = encoder.getInputBuffer(encInIdx)
                                if (encInBuf != null) {
                                    pcmBuf.position(decBufferInfo.offset)
                                    pcmBuf.limit(decBufferInfo.offset + decBufferInfo.size)
                                    encInBuf.clear()
                                    encInBuf.put(pcmBuf)
                                    encoder.queueInputBuffer(encInIdx, 0, decBufferInfo.size, decBufferInfo.presentationTimeUs, 0)
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(decOutIdx, false)

                        if ((decBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderDone = true
                            val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                            if (encInIdx >= 0) {
                                encoder.queueInputBuffer(encInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                        }
                    }
                }

                // 3. Coleta dados AAC codificados e grava no Muxer
                val encOutIdx = encoder.dequeueOutputBuffer(encBufferInfo, timeoutUs)
                if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = encoder.outputFormat
                    aacTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (encOutIdx >= 0) {
                    val aacBuf = encoder.getOutputBuffer(encOutIdx)
                    if (aacBuf != null && encBufferInfo.size > 0 && muxerStarted) {
                        if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            aacBuf.position(encBufferInfo.offset)
                            aacBuf.limit(encBufferInfo.offset + encBufferInfo.size)
                            muxer.writeSampleData(aacTrackIndex, aacBuf, encBufferInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(encOutIdx, false)

                    if ((encBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderDone = true
                    }
                }
            }

            AppLogger.i("AudioTranscoder", "Transcodificação para AAC concluída com sucesso")
            return true

        } catch (e: Exception) {
            AppLogger.e("AudioTranscoder", "Falha na transcodificação de áudio para AAC", e)
            return false
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop(); muxer?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer?,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onMuxerStart: (Int, Boolean) -> Unit
    ) {
        var videoTrackIndex = -1
        var muxerStarted = false

        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break else continue
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                videoTrackIndex = muxer?.addTrack(newFormat) ?: -1
                muxer?.start()
                muxerStarted = true
                onMuxerStart(videoTrackIndex, true)
            } else if (outputBufferIndex >= 0) {
                val encodedData: ByteBuffer = encoder.getOutputBuffer(outputBufferIndex) ?: continue

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0 && muxer != null) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(0, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(outputBufferIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun muxVideoAndAudio(videoFile: File, audioFile: File, outputFile: File, maxDurationUs: Long) {
        if (!videoFile.exists()) throw Exception("Arquivo temporário de vídeo não encontrado")

        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoFile.absolutePath)

        val audioExtractor = MediaExtractor()
        var hasAudio = false
        try {
            audioExtractor.setDataSource(audioFile.absolutePath)
            hasAudio = true
        } catch (e: Exception) {
            AppLogger.e("Muxer", "Erro ao ler arquivo de áudio", e)
        }

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var outVideoTrack = -1
        var outAudioTrack = -1

        // Configura faixa de vídeo
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                videoExtractor.selectTrack(i)
                outVideoTrack = muxer.addTrack(format)
                break
            }
        }

        // Configura faixa de áudio se existir
        var inAudioTrack = -1
        if (hasAudio) {
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    inAudioTrack = i
                    outAudioTrack = muxer.addTrack(format)
                    AppLogger.i("Muxer", "Faixa de áudio adicionada ao MP4 final: $mime")
                    break
                }
            }
        }

        muxer.start()

        val bufferSize = 1024 * 1024
        val byteBuffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        // Copia amostras de vídeo
        if (outVideoTrack != -1) {
            while (true) {
                val sampleSize = videoExtractor.readSampleData(byteBuffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags

                muxer.writeSampleData(outVideoTrack, byteBuffer, bufferInfo)
                videoExtractor.advance()
            }
        }

        // Copia amostras de áudio
        if (hasAudio && outAudioTrack != -1 && inAudioTrack != -1) {
            var audioSamplesCount = 0
            while (true) {
                val sampleSize = audioExtractor.readSampleData(byteBuffer, 0)
                if (sampleSize < 0) break

                val sampleTime = audioExtractor.sampleTime
                if (sampleTime > maxDurationUs + 500_000L) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags

                muxer.writeSampleData(outAudioTrack, byteBuffer, bufferInfo)
                audioSamplesCount++
                audioExtractor.advance()
            }
            AppLogger.i("Muxer", "Gravadas $audioSamplesCount amostras de áudio no vídeo final")
        }

        try {
            muxer.stop()
            muxer.release()
            videoExtractor.release()
            if (hasAudio) audioExtractor.release()
        } catch (e: Exception) {
            AppLogger.e("Muxer", "Erro ao finalizar muxer de vídeo/áudio", e)
        }
    }

    private fun calculateCenterInsideRect(srcW: Int, srcH: Int, destW: Int, destH: Int): RectF {
        val srcRatio = srcW.toFloat() / srcH.toFloat()
        val destRatio = destW.toFloat() / destH.toFloat()

        var finalW = destW.toFloat()
        var finalH = destH.toFloat()

        if (srcRatio > destRatio) {
            finalH = destW / srcRatio
        } else {
            finalW = destH * srcRatio
        }

        val left = (destW - finalW) / 2f
        val top = (destH - finalH) / 2f
        return RectF(left, top, left + finalW, top + finalH)
    }
}
