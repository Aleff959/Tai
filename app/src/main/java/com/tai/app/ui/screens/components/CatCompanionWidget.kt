package com.tai.app.ui.screens.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tai.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val PAL = mapOf(
    '#' to Color(0xFF1A1423),
    'W' to Color(0xFFFFFFFF),
    'w' to Color(0xFFDCE1F0),
    's' to Color(0xFFB4BDD6),
    'O' to Color(0xFFF97316),
    'o' to Color(0xFFEA580C),
    'P' to Color(0xFFF472B6),
    'R' to Color(0xFFEF4444),
    'Y' to Color(0xFFFACC15),
    'y' to Color(0xFFCA8A04),
    'E' to Color(0xFF1A1423),
    'S' to Color(0xFFFFFFFF)
)

private val SPR_SIT = arrayOf(
    "......##................##......",
    ".....#OO#..............#OO#.....",
    "....#OPPo#............#oPPO#....",
    "....#OPPOo#..........#oPOPO#....",
    "...#oPPPPOo##########oOPPPPo#...",
    "..#OOoWWWWWWWWWWWWWWWWWWWWoOO#..",
    ".#OOoWWWWWWWWWWWWWWWWWWWWWWoOO#.",
    ".#OoWWWWWWWWWWWWWWWWWWWWWWWWoO#.",
    ".#WWWWWW...WWWWWWWWWW...WWWWWW#.",
    ".#WWWWWW...WWWWWWWWWW...WWWWWW#.",
    ".#WWWWWW...WWWWWWWWWW...WWWWWW#.",
    ".#WWWWPPWWWWWWWPpWWWWWWWPPWWWW#.",
    "..#WWWWWWWWWWW#WW#WWWWWWWWWWW#..",
    "...#wWWWWWWWW#WWWW#WWWWWWWWw#...",
    ".....###RRRRRRRRRRRRRRRR###.....",
    ".......#RRRRRRYyyYRRRRRR#.......",
    "......#WWWWWWW#YY#WWWWWWW#......",
    ".....#WWWWWWWWW##WWWWWWWWW#..##.",
    ".....#WWWWWWWWWWWWWWWWWWWW#.#OO#",
    "....#OoWWWWWsWWWWWWsWWWWWW#.#Oo#",
    "....#OOoWWWWsWWWWWWsWWWWWW#.#Oo#",
    "....#OOoWWWWsWWWWWWsWWWWWW##Oo#.",
    "....#oOoWWWWsWWWWWWsWWWWWWoOO#..",
    ".....#wwwwwwsWWWWWWswwwwwwOO#...",
    ".....#WW#WW##WW#WW##WW#WW###....",
    "......#####..#####..#####......."
)

private val SPR_WALK_BODY = arrayOf(
    "..........##............##......",
    ".........#OO#..........#OO#.....",
    "........#OPPo#........#oPPO#....",
    "........#OPPOo########oPOPO#....",
    ".......#oPPPPOoWWWWWWoOPPPPo#...",
    "......#OOoWWWWWWWWWWWWWWWWoOO#..",
    ".....#OOoWWWWWWWWWWWWWWWWWWoOO#.",
    ".....#OoWWWWWWWWWWWWWWWWWWWWoO#.",
    ".....#WWWWWWWW...WWWWWW...WWWW#.",
    ".....#WWWWWWWW...WWWWWW...WWWW#.",
    ".....#WWWWWWWW...WWWWWW...WWWW#.",
    ".##..#WWWWWWPPWWWWWWPPWWWWPPWW#.",
    "#OO#..#WWWWWWWWWWWW##WWWWWWWW#..",
    "#Oo#...#wWWWWWWWWWWWWWWWWWWw#...",
    ".#Oo#....###RRRRRRRRRRRRR###....",
    ".#Oo#..#####RRRRRRRRRYyyY#......",
    "..#Oo##OOoWWWWWWWWWWW#YY#W#.....",
    "..#OOoOOoWWWWWWWWWWWWWWWWWW#....",
    "...#oOOoWWWWWWWWWWWWWWWWWwW#....",
    "....#WWWWWWWWooOOooWWWWWWwW#....",
    "....#wWWWWWWWoOOOOoWWWWWWw#.....",
    ".....#wwwwwwwwWWWWwwwwwww#......"
)

private val WALK_LEGS = arrayOf(
    arrayOf(
        "....#WW#...#ww#....#WW#...#ww#..",
        "...#WW#....#ww#....#WW#....#ww#.",
        "...#WW#....#ww#....#WW#....#ww#.",
        "...###.....###.....###.....###.."
    ),
    arrayOf(
        ".....#WW#.#ww#......#WW#.#ww#...",
        ".....#WW#.#ww#......#WW#.#ww#...",
        "......#WW#ww#........#WW#ww#....",
        "......######.........######....."
    ),
    arrayOf(
        ".....#ww#..#WW#.....#ww#..#WW#..",
        "....#ww#....#WW#...#ww#....#WW#.",
        "....#ww#....#WW#...#ww#....#WW#.",
        "....###.....###....###.....###.."
    ),
    arrayOf(
        "....#WW#...#ww#.....#ww#..#WW#..",
        "...#WW#.....#ww#....#ww#...#WW#.",
        "...###......###.....###....###..",
        "................................"
    )
)

private val SPR_STRETCH = arrayOf(
    "................................",
    "................................",
    "................................",
    "...........##............##.....",
    "..........#OO#..........#OO#....",
    ".##......#OPPo#........#oPPO#...",
    "#OO#.....#OPPOo########oPOPO#...",
    "#Oo#....#oPPPPOoWWWWWWoOPPPPo#..",
    ".#Oo#..#OOoWWWWWWWWWWWWWWWWoOO#.",
    ".#Oo#.#OOoWWWWWWWWWWWWWWWWWWoOO#",
    "..#Oo##OoWWWWWWWWWWWWWWWWWWWWoO#",
    "..#OO##WWWWWWW###WWWWWW###WWWW#.",
    "...#O##WWWWWW#...#WWWW#...#WWW#.",
    "...#OO#WWWWWWPPWWWWWWPPWWWWPPW#.",
    "...#OOo###WWWWWWWWWWW##WWWWWW#..",
    "...#OOoWWW###RRRRRRRRRRRRR###...",
    "...#oOoWWWWWW#RRRRRRRRYyyY#.....",
    "....#WWWWWWWWWWWWWWWWW#YY#W#....",
    "....#WW#WWWWWWWWWWWWWWWWWWWW#...",
    "....#WW#.###WWWWWWWWWWWWWWWW###.",
    "....#WW#....###WWWWWWWWWW#WWWWW#",
    "...#WW#........####WWWWWW#WWWWW#",
    "...#WW#............######..#####",
    "...#WW#.................#WWWWWW#",
    "...#WW#..................#WWWWWW#",
    "...###....................######"
)

private val SPR_ROLL = arrayOf(
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "....###......###...###..........",
    "...#WPW#....#WPW#.#WPW#..###....",
    "...#WWW#....#WWW#.#WWW#.#WPW#...",
    "....#WW######WWW###WWW#.#WWW#...",
    "...#WWWWWWWWWWWWWWWWWW###WW#....",
    "..#WWWWWWPPPPWWWWWWWWWRRRWW#....",
    "..#WWWWWWPPPPWWWWWWWWWRYYRW#....",
    ".#OOoWWWWWWWWWWWWWWWWWRRRWWW#...",
    "#OOoWWWWWWWWWWWWWWWWWW#...#WW#..",
    "#OoWWWWWWWWWWWWWWWWWWWW###WWW#..",
    "#OO#..#WWWWWWWWWWWWWWPPWWWWWW#..",
    ".##....#oOOoWWWWWWWWW##WWWWoo#..",
    "........#OOoWWWWWWWWWWWWWWoOO#..",
    ".........#oOOOOOOOOOOOOOOoOO#...",
    "..........###oOO#......#OOo#....",
    ".............###........###.....",
    "................................"
)

private val SPR_EAT = arrayOf(
    "................................",
    "................................",
    "................................",
    "................................",
    ".............##............##...",
    "............#OO#..........#OO#..",
    "...........#OPPo#........#oPPO#.",
    "..##.......#OPPOo########oPOPO#.",
    ".#OO#.....#oPPPPOoWWWWWWoOPPPPo#",
    ".#Oo#....#OOoWWWWWWWWWWWWWWWWoOO",
    "..#Oo#..#OOoWWWWWWWWWWWWWWWWWWoO",
    "..#Oo#..#OoWWWWWWWWWWWWWWWWWWWWo",
    "...#Oo#.#WWWWWWW###WWWWWW###WWWW",
    "...#OO###WWWWWW#...#WWWW#...#WWW",
    "....#OOoWWWWWWWPPWWWWWWPPWWWWPPW",
    "....#OOoWWWWWWWWWWWWWWW##WWWWWW#",
    "....#oOoWWWWWWWW###RRRRRRRRR###.",
    ".....#WWWWWWWWWWWWW#RRRRRYyyY#..",
    ".....#wWWWWWWWWWWWWWWWWWW#YY#...",
    ".....#wwWWWWWWooOOooWWWWWWWW#...",
    "......#wwwwwwwOOOOOOwwwwwww#....",
    "......#WW#..#ww#..#WW#..#ww#....",
    ".....#WW#...#ww#..#WW#..#ww#....",
    ".....#WW#...###...#WW#..###.....",
    ".....###..........###...........",
    "................................"
)

private val SPR_SLEEP_CURL = arrayOf(
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "........##............##........",
    ".......#OO#..........#OO#.......",
    "......#OPPo##########oPPO#......",
    ".....#oPPPPOoWWWWWWoOPPPPo#.....",
    "....#OOoWWWWWWWWWWWWWWWWoOO#....",
    "...#OOoWWWWWWWWWWWWWWWWWWoOO##..",
    "...#OoWWWWWWWWWWWWWWWWWWWWoOOo#.",
    "..#WWWWWW#...#WWWWWW#...#WWWWoo#",
    "..#WWWWWWW###WWWWWWWW###WWWWWoo#",
    "..#WWWWWWPPWWWWWWPpWWWWPPWWWWoo#",
    "..#OoWWWWWWWWWWW#WW#WWWWWWWWooO#",
    "..#OOoWWWWWWWW#######WWWWWWooOO#",
    "...#OOoWWWWWW#OOOOOOo#WWWWooOO#.",
    "...#oOOoWWWW#OOoOOOOoO#WWooOO#..",
    "....#wwwwwww#OOoOOOOoOO#####....",
    ".....#WW#WW##oOOOOOOOOo#........",
    "......#####..##########.........",
    "................................"
)

private class CatParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Int,
    val type: String
)

@Composable
fun CatCompanionWidget(
    modifier: Modifier = Modifier,
    trailingAction: @Composable (() -> Unit)? = null
) {
    val stageW = 124
    val stageH = 44
    val sprW = 32
    val floorY = 39f

    var catX by remember { mutableFloatStateOf(46f) }
    var catY by remember { mutableFloatStateOf(floorY - 26f) }
    var catVx by remember { mutableFloatStateOf(0f) }
    var catVy by remember { mutableFloatStateOf(0f) }
    var catDir by remember { mutableIntStateOf(1) }
    var catMode by remember { mutableStateOf("SIT") }
    var catTimer by remember { mutableIntStateOf(0) }
    var frameTick by remember { mutableIntStateOf(0) }
    var walkTick by remember { mutableFloatStateOf(0f) }
    var targetX by remember { mutableStateOf<Float?>(null) }
    var lookX by remember { mutableFloatStateOf(0f) }
    var lookY by remember { mutableFloatStateOf(0f) }
    var targetLookX by remember { mutableFloatStateOf(0f) }
    var targetLookY by remember { mutableFloatStateOf(0f) }
    var blink by remember { mutableIntStateOf(0) }

    var laserActive by remember { mutableStateOf(false) }
    var laserX by remember { mutableFloatStateOf(90f) }
    val laserY = floorY - 2f

    var yarnActive by remember { mutableStateOf(false) }
    var yarnX by remember { mutableFloatStateOf(85f) }
    var yarnY by remember { mutableFloatStateOf(floorY - 7f) }
    var yarnVx by remember { mutableFloatStateOf(-1.2f) }
    var yarnVy by remember { mutableFloatStateOf(0f) }

    var fishActive by remember { mutableStateOf(false) }
    var fishX by remember { mutableFloatStateOf(78f) }
    val fishY = floorY - 5f
    var fishEating by remember { mutableStateOf(false) }

    // Sensores de inclinação do aparelho
    val context = LocalContext.current
    var tiltX by remember { mutableFloatStateOf(0f) }
    var slidePuffTimer by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.isNotEmpty()) {
                    // No Android em modo retrato:
                    // Inclinação para a direita: event.values[0] fica negativo (-X)
                    // Inclinação para a esquerda: event.values[0] fica positivo (+X)
                    // Invertemos para que tiltX > 0 signifique deslizar para a direita.
                    val rawX = -event.values[0]
                    tiltX = tiltX * 0.7f + rawX * 0.3f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    var isManualControl by remember { mutableStateOf(false) }
    var activeBtnIndex by remember { mutableStateOf<Int?>(null) }
    var autoChangeTimer by remember { mutableIntStateOf(100) }

    var bubbleText by remember { mutableStateOf("") }
    var bubbleTicks by remember { mutableIntStateOf(0) }

    val particles = remember { mutableStateListOf<CatParticle>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun say(text: String, ticks: Int = 85) {
        bubbleText = text
        bubbleTicks = ticks
    }

    fun addParticle(px: Float, py: Float, type: String) {
        particles.add(
            CatParticle(
                x = px,
                y = py,
                vx = (Random.nextFloat() - 0.5f) * 0.45f,
                vy = -0.35f - Random.nextFloat() * 0.25f,
                life = 36,
                type = type
            )
        )
    }

    fun triggerAnim(num: Int, fromAuto: Boolean = false) {
        when (num) {
            1 -> {
                catMode = "WALK"
                targetX = null
                activeBtnIndex = 1
                say("Passeando! 🐾", 75)
            }
            2 -> {
                catMode = "LASER"
                activeBtnIndex = 2
                say("Cadê o laser?! 🔴", 85)
            }
            3 -> {
                catMode = "YARN"
                yarnX = if (catX > stageW / 2f) 20f else stageW - 20f
                yarnVx = if (catX > stageW / 2f) 1.4f else -1.4f
                activeBtnIndex = 3
                say("Meu novelo! 🧶", 85)
            }
            4 -> {
                catMode = "JUMP"
                catVy = -3.1f
                catVx = catDir * 0.9f
                activeBtnIndex = 4
                addParticle(catX + 14f, catY, "star")
                say("Yaaay! ⭐", 70)
            }
            5 -> {
                catMode = "STRETCH"
                catTimer = 75
                activeBtnIndex = 5
                say("Preguicinha...", 85)
            }
            6 -> {
                catMode = "ROLL"
                catTimer = 90
                activeBtnIndex = 6
                addParticle(catX + 14f, catY + 4f, "heart")
                say("Carinho na barriga! 🙃", 95)
            }
            7 -> {
                catMode = "GROOM"
                catTimer = 90
                activeBtnIndex = 7
                say("Hora do banho! 🧼", 90)
            }
            8 -> {
                catMode = "FISH"
                fishX = if (catX > stageW / 2f) 24f else stageW - 24f
                fishEating = false
                catTimer = 80
                activeBtnIndex = 8
                say("Peixinho fresco! 🐟", 90)
            }
            9 -> {
                if (!fromAuto && catMode == "SLEEP") {
                    catMode = "SIT"
                    activeBtnIndex = null
                    say("Acordei! ☀️", 75)
                } else {
                    catMode = "SLEEP"
                    activeBtnIndex = 9
                    say("Zzz... Boa noite 🌙", 105)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16L)
            frameTick++
            if (bubbleTicks > 0) bubbleTicks--

            lookX += (targetLookX - lookX) * 0.25f
            lookY += (targetLookY - lookY) * 0.25f

            if (blink > 0) blink--
            else if (Random.nextFloat() < 0.015f) blink = 5

            // Detecção de inclinação do aparelho (escorregando)
            val tiltThreshold = 1.8f
            val isTilting = abs(tiltX) > tiltThreshold
            if (isTilting && catMode != "JUMP") {
                catMode = "SLIDE"
                val slideForce = ((abs(tiltX) - tiltThreshold) * 0.42f).coerceIn(0.6f, 2.8f)
                catDir = if (tiltX > 0) 1 else -1
                catVx = catDir * slideForce

                // Efeito de susto / poeira escorregando
                slidePuffTimer++
                if (slidePuffTimer % 8 == 0) {
                    val puffX = if (catDir > 0) catX + 2f else catX + sprW - 2f
                    addParticle(puffX, floorY - 2f, "sparkle")
                }
                if (bubbleTicks <= 0 && Random.nextFloat() < 0.08f) {
                    val slideQuotes = listOf("Wooooah! 🙀", "Eeeita! 🐾", "Escorregandooo! 💨", "Seguraaa! 😼")
                    say(slideQuotes[Random.nextInt(slideQuotes.size)], 45)
                }
            } else if (catMode == "SLIDE" && !isTilting) {
                // Ao nivelar o celular, desacelera e volta a sentar
                catVx *= 0.6f
                if (abs(catVx) < 0.2f) {
                    catVx = 0f
                    catMode = "SIT"
                    activeBtnIndex = null
                    slidePuffTimer = 0
                }
            }

            if (!isManualControl && catMode != "SLIDE") {
                autoChangeTimer--
                if (autoChangeTimer <= 0) {
                    val pool = intArrayOf(1, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
                    val choice = pool[Random.nextInt(pool.size)]
                    if (choice == 0) {
                        catMode = "SIT"
                        activeBtnIndex = null
                        autoChangeTimer = 140 + Random.nextInt(100)
                    } else {
                        triggerAnim(choice, fromAuto = true)
                        autoChangeTimer = 210 + Random.nextInt(140)
                    }
                }
                if (catMode == "LASER") {
                    laserX = 62f + sin(frameTick * 0.06f) * 44f
                }
            }

            when (catMode) {
                "SIT" -> catVx = 0f
                "PET" -> {
                    catVx = 0f
                    catTimer--
                    if (catTimer % 12 == 0) addParticle(catX + 14f, catY, "heart")
                    if (catTimer <= 0) catMode = "SIT"
                }
                "WALK" -> {
                    if (targetX == null) {
                        targetX = 8f + Random.nextFloat() * (stageW - sprW - 16f)
                    }
                    val dx = (targetX ?: catX) - catX
                    if (abs(dx) < 2f) {
                        if (Random.nextFloat() < 0.03f) {
                            targetX = 8f + Random.nextFloat() * (stageW - sprW - 16f)
                        } else {
                            catVx = 0f
                        }
                    } else {
                        catVx = if (dx > 0f) 0.75f else -0.75f
                        catDir = if (dx > 0f) 1 else -1
                    }
                }
                "LASER" -> {
                    laserActive = true
                    val ldx = (laserX - 16f) - catX
                    if (abs(ldx) > 4f) {
                        catVx = if (ldx > 0f) 1.35f else -1.35f
                        catDir = if (ldx > 0f) 1 else -1
                    } else {
                        catVx = 0f
                        if (catY >= floorY - 26f && Random.nextFloat() < 0.08f) {
                            catVy = -2.2f
                            addParticle(laserX, laserY - 4f, "star")
                        }
                    }
                }
                "YARN" -> {
                    yarnActive = true
                    yarnX += yarnVx
                    yarnY += yarnVy
                    if (yarnY < floorY - 7f) yarnVy += 0.2f
                    else {
                        yarnY = floorY - 7f
                        yarnVy = 0f
                    }
                    if (yarnX < 8f) {
                        yarnX = 8f
                        yarnVx = abs(yarnVx)
                    }
                    if (yarnX > stageW - 10f) {
                        yarnX = stageW - 10f
                        yarnVx = -abs(yarnVx)
                    }
                    val ydx = (yarnX - 16f) - catX
                    catVx = if (ydx > 0f) 1.15f else -1.15f
                    catDir = if (ydx > 0f) 1 else -1
                    if (abs(ydx) < 10f && Random.nextFloat() < 0.15f) {
                        yarnVx = catDir * (1.6f + Random.nextFloat() * 0.8f)
                        yarnVy = -1.8f
                        addParticle(yarnX, yarnY, "star")
                    }
                }
                "JUMP" -> {
                    if (catY >= floorY - 26f && catVy == 0f) {
                        catMode = "SIT"
                        activeBtnIndex = null
                    }
                }
                "STRETCH" -> {
                    catVx = 0f
                    catTimer--
                    if (catTimer <= 0) {
                        catMode = "SIT"
                        activeBtnIndex = null
                    }
                }
                "ROLL" -> {
                    catVx = 0f
                    catTimer--
                    if (catTimer % 16 == 0) addParticle(catX + 14f, catY + 6f, "heart")
                    if (catTimer <= 0) {
                        catMode = "SIT"
                        activeBtnIndex = null
                    }
                }
                "GROOM" -> {
                    catVx = 0f
                    catTimer--
                    if (catTimer % 18 == 0) addParticle(catX + 14f, catY + 4f, "sparkle")
                    if (catTimer <= 0) {
                        catMode = "SIT"
                        activeBtnIndex = null
                    }
                }
                "FISH" -> {
                    fishActive = true
                    val targetCatX = if (fishX > stageW / 2f) fishX - 28f else fishX - 4f
                    val fdx = targetCatX - catX
                    if (!fishEating && abs(fdx) > 2f) {
                        catVx = if (fdx > 0f) 1.1f else -1.1f
                        catDir = if (fdx > 0f) 1 else -1
                    } else {
                        catVx = 0f
                        catDir = if (fishX >= catX + 16f) 1 else -1
                        fishEating = true
                        catTimer--
                        if (catTimer % 14 == 0) addParticle(catX + 16f, catY + 4f, "heart")
                        if (catTimer <= 0) {
                            fishActive = false
                            fishEating = false
                            catMode = "SIT"
                            activeBtnIndex = null
                            say("Barriguinha cheia! 😋", 85)
                        }
                    }
                }
                "SLEEP" -> {
                    catVx = 0f
                    if (frameTick % 35 == 0) addParticle(catX + 18f, catY + 6f, "zzz")
                }
                "SLIDE" -> {
                    // O movimento é regido pela inclinação no sensor
                }
            }

            if (catMode != "LASER") laserActive = false
            if (catMode != "YARN") yarnActive = false
            if (catMode != "FISH") {
                fishActive = false
                fishEating = false
            }

            catX += catVx
            catY += catVy

            if (catY < floorY - 26f) catVy += 0.22f
            else {
                catY = floorY - 26f
                catVy = 0f
            }

            catX = max(2f, min((stageW - sprW - 2).toFloat(), catX))
            if (abs(catVx) > 0.1f) {
                walkTick += abs(catVx) * 0.28f
            }

            val iter = particles.listIterator()
            while (iter.hasNext()) {
                val p = iter.next()
                p.x += p.vx
                p.y += p.vy
                p.life--
                if (p.life <= 0) iter.remove()
            }
        }
    }

    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Área do Palco + Balão acima da Sprite
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: continue
                            if (canvasSize.width > 0 && canvasSize.height > 0) {
                                val scaleX = canvasSize.width.toFloat() / stageW
                                val scaleY = canvasSize.height.toFloat() / stageH
                                val gx = pos.x / scaleX
                                val gy = pos.y / scaleY

                                targetLookX = max(-1f, min(1f, (gx - (catX + 16f)) / 18f))
                                targetLookY = max(-1f, min(1f, (gy - (catY + 10f)) / 18f))

                                if (catMode == "LASER" && isManualControl) {
                                    laserX = max(8f, min((stageW - 8).toFloat(), gx))
                                }

                                if (event.type == PointerEventType.Press) {
                                    // Clicou acima da cabeça do gato -> PULA!
                                    if (gx >= catX - 10f && gx <= catX + sprW + 10f && gy < catY + 4f) {
                                        triggerAnim(4)
                                    } else if (gx >= catX && gx <= catX + sprW && gy >= catY + 4f) {
                                        catMode = "PET"
                                        catTimer = 55
                                        activeBtnIndex = null
                                        addParticle(catX + 14f, catY, "heart")
                                        say("Purrrr... ❤️", 85)
                                    } else {
                                        catMode = "WALK"
                                        targetX = max(4f, min((stageW - sprW - 4).toFloat(), gx - 16f))
                                        activeBtnIndex = 1
                                        say("Indo! 🐾", 65)
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pxW = size.width / stageW
                val pxH = size.height / stageH

                fun drawGridPixel(gx: Int, gy: Int, color: Color, w: Int = 1, h: Int = 1) {
                    if (gx + w < 0 || gx >= stageW || gy + h < 0 || gy >= stageH) return
                    drawRect(
                        color = color,
                        topLeft = Offset(gx * pxW, gy * pxH),
                        size = Size(w * pxW + 0.4f, h * pxH + 0.4f)
                    )
                }

                // Tapete / linha de piso
                drawGridPixel(6, floorY.toInt(), Color(0xFF1A1F33), stageW - 12, 3)
                drawGridPixel(10, floorY.toInt(), Color(0xFF2B3354), stageW - 20, 1)

                val cx = catX.toInt()
                val cy = catY.toInt()

                // Sombra do gato
                drawGridPixel(cx + 5, floorY.toInt() - 1, Color(0x66000000), 22, 2)

                val isWalking = (catMode == "WALK" || catMode == "LASER" || catMode == "YARN" || (catMode == "FISH" && !fishEating) || abs(catVx) > 0.1f) && catMode != "JUMP" && catMode != "SLIDE"
                val isSidePose = isWalking || catMode == "JUMP" || catMode == "STRETCH" || catMode == "FISH" || catMode == "SLIDE"
                val breath = if (sin(frameTick * 0.08f) > 0.3f) 1 else 0
                val purr = if (catMode == "PET" && (frameTick / 2) % 2 == 0) 1 else 0

                fun drawCatPixel(localX: Int, localY: Int, color: Color, w: Int = 1, h: Int = 1) {
                    val finalX = if (isSidePose && catDir == -1) {
                        cx + (sprW - w - localX)
                    } else {
                        cx + localX
                    }
                    val finalY = cy + localY
                    drawGridPixel(finalX, finalY, color, w, h)
                }

                when {
                    catMode == "SLEEP" -> {
                        for (r in SPR_SLEEP_CURL.indices) {
                            val row = SPR_SLEEP_CURL[r]
                            val bob = if (r < 21) breath else 0
                            for (c in row.indices) {
                                val ch = row[c]
                                if (ch == '.') continue
                                PAL[ch]?.let { drawCatPixel(c, r + bob, it) }
                            }
                        }
                    }
                    catMode == "STRETCH" -> {
                        for (r in SPR_STRETCH.indices) {
                            val row = SPR_STRETCH[r]
                            for (c in row.indices) {
                                val ch = row[c]
                                if (ch == '.') continue
                                PAL[ch]?.let { drawCatPixel(c, r, it) }
                            }
                        }
                    }
                    catMode == "ROLL" -> {
                        val wiggle = sin(frameTick * 0.25f).roundToInt()
                        for (r in SPR_ROLL.indices) {
                            val row = SPR_ROLL[r]
                            for (c in row.indices) {
                                val ch = row[c]
                                if (ch == '.') continue
                                var dx = c
                                var dy = r
                                if (r <= 12) dy += if (c < 14) wiggle else -wiggle
                                if (r in 17..20) dx += wiggle
                                PAL[ch]?.let { drawCatPixel(dx, dy, it) }
                            }
                        }
                    }
                    catMode == "FISH" && fishEating -> {
                        val chew = (frameTick / 3) % 2
                        for (r in SPR_EAT.indices) {
                            val row = SPR_EAT[r]
                            val bob = if (r < 18) chew else 0
                            for (c in row.indices) {
                                val ch = row[c]
                                if (ch == '.') continue
                                PAL[ch]?.let { drawCatPixel(c, r + bob, it) }
                            }
                        }
                    }
                    isWalking || catMode == "JUMP" || catMode == "SLIDE" -> {
                        val legFrame = if (catMode == "JUMP" || catMode == "SLIDE") 0 else (walkTick.toInt() % 4)
                        val bodyBob = if (legFrame == 1 || legFrame == 3) 1 else 0
                        val tailWag = if (catMode == "SLIDE") sin(frameTick * 0.45f).roundToInt() * 2 else sin(frameTick * 0.2f).roundToInt()

                        for (r in SPR_WALK_BODY.indices) {
                            val row = SPR_WALK_BODY[r]
                            for (c in row.indices) {
                                val ch = row[c]
                                if (ch == '.') continue
                                var dx = c
                                if (c <= 5 && r in 11..17) dx += tailWag
                                PAL[ch]?.let { drawCatPixel(dx, r + bodyBob, it) }
                            }
                        }

                        val hy = bodyBob
                        // Olhos arregalados e surpresos durante o SLIDE
                        if (catMode == "SLIDE") {
                            drawCatPixel(13, hy + 7, PAL['#']!!, 4, 4)
                            drawCatPixel(22, hy + 7, PAL['#']!!, 4, 4)
                            drawCatPixel(14, hy + 8, PAL['W']!!, 2, 2)
                            drawCatPixel(23, hy + 8, PAL['W']!!, 2, 2)
                            drawCatPixel(15, hy + 8, PAL['E']!!, 1, 1)
                            drawCatPixel(24, hy + 8, PAL['E']!!, 1, 1)
                        } else {
                            drawCatPixel(14, hy + 8, PAL['E']!!, 3, 3)
                            drawCatPixel(23, hy + 8, PAL['E']!!, 3, 3)
                            drawCatPixel(15, hy + 8, PAL['S']!!, 1, 1)
                            drawCatPixel(24, hy + 8, PAL['S']!!, 1, 1)
                        }

                        drawCatPixel(29, hy + 10, PAL['#']!!, 2, 1)
                        drawCatPixel(29, hy + 12, PAL['#']!!, 2, 1)

                        val legs = if (catMode == "SLIDE") {
                            // Patas esticadas/fincadas no chão derrapando
                            arrayOf(
                                "....#WW#.....#ww#.....#ww#..#WW#.",
                                "...#WW#.......#ww#...#ww#....#WW#",
                                "...###.........###...###.....###.",
                                "................................"
                            )
                        } else {
                            WALK_LEGS[legFrame]
                        }

                        for (r in legs.indices) {
                            val row = legs[r]
                            for (c in row.indices) {
                                val ch = row[c]
                                if (ch == '.') continue
                                PAL[ch]?.let { drawCatPixel(c, 22 + r, it) }
                            }
                        }
                    }
                    else -> {
                        val tailWag = sin(frameTick * 0.1f).roundToInt()
                        val groomTilt = if (catMode == "GROOM" && sin(frameTick * 0.3f) > 0f) 1 else 0

                        for (r in SPR_SIT.indices) {
                            val row = SPR_SIT[r]
                            val rowBob = if (r < 22) (breath + purr + groomTilt) else 0
                            for (c in row.indices) {
                                val ch = row[c]
                                if (ch == '.') continue
                                var dx = c
                                if (c >= 27 && r in 17..21) dx += tailWag
                                PAL[ch]?.let { drawCatPixel(dx, r + rowBob, it) }
                            }
                        }

                        val headY = breath + purr + groomTilt
                        val lx = lookX.roundToInt()
                        val ly = lookY.roundToInt()

                        if (catMode == "PET" || catMode == "GROOM") {
                            drawCatPixel(7, headY + 9, PAL['#']!!)
                            drawCatPixel(8, headY + 8, PAL['#']!!)
                            drawCatPixel(9, headY + 9, PAL['#']!!)
                            drawCatPixel(20, headY + 9, PAL['#']!!)
                            drawCatPixel(21, headY + 8, PAL['#']!!)
                            drawCatPixel(22, headY + 9, PAL['#']!!)
                        } else if (blink > 0) {
                            drawCatPixel(7, headY + 9, PAL['#']!!, 3, 1)
                            drawCatPixel(20, headY + 9, PAL['#']!!, 3, 1)
                        } else {
                            drawCatPixel(7, headY + 8, PAL['E']!!, 3, 3)
                            drawCatPixel(20, headY + 8, PAL['E']!!, 3, 3)
                            val sx = if (lx >= 0) 1 else 0
                            val sy = if (ly > 0) 1 else 0
                            drawCatPixel(7 + sx, headY + 8 + sy, PAL['S']!!)
                            drawCatPixel(20 + sx, headY + 8 + sy, PAL['S']!!)
                        }

                        drawCatPixel(2, headY + 10, PAL['#']!!, 3, 1)
                        drawCatPixel(2, headY + 12, PAL['#']!!, 3, 1)
                        drawCatPixel(27, headY + 10, PAL['#']!!, 3, 1)
                        drawCatPixel(27, headY + 12, PAL['#']!!, 3, 1)

                        if (catMode == "GROOM") {
                            val cycle = sin(frameTick * 0.3f)
                            val pawX = 9
                            val pawY = headY + 10 + (cycle * 2f).roundToInt()
                            if (cycle > 0f) {
                                drawCatPixel(14, headY + 12, PAL['P']!!, 2, 2)
                            }
                            drawCatPixel(pawX - 1, pawY - 1, PAL['#']!!, 5, 5)
                            drawCatPixel(pawX, pawY, PAL['W']!!, 3, 3)
                            drawCatPixel(pawX + 1, pawY + 1, PAL['P']!!, 1, 1)
                        }
                    }
                }

                // Tigela com peixinho (Animação 8)
                if (fishActive) {
                    val fx = fishX.toInt()
                    val fy = fishY.toInt()
                    drawGridPixel(fx - 5, fy + 1, Color(0xFF1A1423), 11, 4)
                    drawGridPixel(fx - 4, fy + 2, Color(0xFFF43F5E), 9, 2)
                    if (!fishEating || catTimer > 20) {
                        drawGridPixel(fx - 3, fy - 1, Color(0xFF38BDF8), 5, 3)
                        drawGridPixel(fx - 2, fy, Color(0xFFBAE6FD), 3, 1)
                        drawGridPixel(fx + 2, fy - 1, Color(0xFFBAE6FD), 2, 2)
                    }
                }

                // Novelo de Lã (Animação 3)
                if (yarnActive) {
                    val yx = yarnX.toInt()
                    val yy = yarnY.toInt()
                    drawGridPixel(yx - 3, floorY.toInt() - 1, Color(0x66000000), 7, 1)
                    drawGridPixel(yx - 2, yy - 3, Color(0xFF831843), 5, 7)
                    drawGridPixel(yx - 3, yy - 2, Color(0xFF831843), 7, 5)
                    drawGridPixel(yx - 2, yy - 2, Color(0xFFEC4899), 5, 5)
                    drawGridPixel(yx - 1, yy - 1, Color(0xFFFBCFE8), 2, 2)
                }

                // Laser Pointer (Animação 2)
                if (laserActive) {
                    val lx = laserX.toInt()
                    val ly = laserY.toInt()
                    drawGridPixel(lx - 2, ly - 2, Color(0x77EF4444), 5, 5)
                    drawGridPixel(lx - 1, ly - 1, Color(0xFFEF4444), 3, 3)
                    drawGridPixel(lx, ly, Color(0xFFFFFFFF), 1, 1)
                }

                // Partículas
                for (p in particles) {
                    val px = p.x.toInt()
                    val py = p.y.toInt()
                    when (p.type) {
                        "heart" -> {
                            val hc = Color(0xFFF43F5E)
                            drawGridPixel(px, py, hc, 2, 1)
                            drawGridPixel(px + 3, py, hc, 2, 1)
                            drawGridPixel(px - 1, py + 1, hc, 7, 2)
                            drawGridPixel(px, py + 3, hc, 5, 1)
                            drawGridPixel(px + 1, py + 4, hc, 3, 1)
                            drawGridPixel(px + 2, py + 5, hc, 1, 1)
                        }
                        "zzz" -> {
                            val zc = Color(0xFFA78BFA)
                            drawGridPixel(px, py, zc, 3, 1)
                            drawGridPixel(px + 1, py + 1, zc, 1, 1)
                            drawGridPixel(px, py + 2, zc, 3, 1)
                        }
                        "star" -> {
                            val sc = Color(0xFFFACC15)
                            drawGridPixel(px + 1, py, sc, 1, 3)
                            drawGridPixel(px, py + 1, sc, 3, 1)
                        }
                        "sparkle" -> {
                            drawGridPixel(px + 1, py, Color(0xFF38BDF8), 1, 3)
                            drawGridPixel(px, py + 1, Color(0xFF38BDF8), 3, 1)
                            drawGridPixel(px + 1, py + 1, Color.White, 1, 1)
                        }
                    }
                }
            }

            // Balão de fala posicionado acima da sprite do gato
            if (bubbleTicks > 0 && canvasSize.width > 0 && canvasSize.height > 0) {
                val headTopRow = when {
                    catMode == "SLEEP" || catMode == "ROLL" -> 8
                    catMode == "STRETCH" || (catMode == "FISH" && fishEating) -> 4
                    else -> 0
                }
                val pxW = canvasSize.width.toFloat() / stageW
                val pxH = canvasSize.height.toFloat() / stageH
                val bubbleCenterX = ((catX + 16f) * pxW).roundToInt()
                val bubbleTopY = max(4, ((catY + headTopRow) * pxH).roundToInt() - with(density) { 28.dp.roundToPx() })

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = max(8, min(canvasSize.width - with(density) { 125.dp.roundToPx() }, bubbleCenterX - with(density) { 55.dp.roundToPx() })),
                                y = bubbleTopY
                            )
                        }
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = bubbleText,
                        color = Color(0xFF1E1B4B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Painel retrátil com as 9 animações (aparece apenas quando clica no mini controle)
        AnimatedVisibility(
            visible = isManualControl,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val animLabels = listOf(
                1 to "1. Passear 🐾",
                2 to "2. Laser 🔴",
                3 to "3. Novelo 🧶",
                4 to "4. Pular ⭐",
                5 to "5. Espreguiçar 🧘",
                6 to "6. Rolar 🙃",
                7 to "7. Banho 🧼",
                8 to "8. Peixe 🐟",
                9 to "9. Dormir 💤"
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E111D))
                    .border(1.dp, CardBorder, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 0 until 3) {
                            val (idx, label) = animLabels[row * 3 + col]
                            val isSelected = activeBtnIndex == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF4338CA) else Color(0xFF1E243D))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF818CF8) else Color(0xFF353F64),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { triggerAnim(idx) }
                                    .padding(vertical = 7.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Barra inferior minimalista apenas com o mini símbolo de controle (🎮)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(Color(0xFF0A0C14)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = if (isManualControl) Color(0xFF4338CA) else Color(0xFF1A1F33),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .size(width = 32.dp, height = 24.dp)
                    .border(
                        1.dp,
                        if (isManualControl) Color(0xFF818CF8) else Color(0xFF333D63),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        isManualControl = !isManualControl
                        if (!isManualControl) {
                            activeBtnIndex = null
                            autoChangeTimer = 40
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = "Controle de Animações",
                        tint = if (isManualControl) Color.White else NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Ação no extremo inferior direito (mesma altura e alinhamento da barra)
            if (trailingAction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                ) {
                    trailingAction()
                }
            }
        }
    }
}
