# ⚡ Taí! — Downloader Universal de Mídias

<p align="center">
  <img src="https://img.shields.io/badge/Vers%C3%A3o-0.0.2%20%7C%20Comp.%206--Fa%C3%ADsca-00F5FF?style=for-the-badge&logo=android&logoColor=black" alt="Versão Atual" />
  <img src="https://img.shields.io/badge/Plataforma-Android%208.0%2B-brightgreen?style=for-the-badge&logo=android" alt="Plataforma" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Licen%C3%A7a-MIT-yellow?style=for-the-badge" alt="Licença" />
</p>

<p align="center">
  <b>Taí!</b> é um aplicativo moderno e ultra-rápido para Android feito em <b>Jetpack Compose</b>, projetado para extrair e baixar vídeos, fotos únicas e carrosséis completos das principais redes sociais com um único toque — além de contar com um companheiro pixel art animado no rodapé e atualizações OTA nativas.
</p>

---

## 🌟 Principais Recursos

- 🎵 **TikTok**:
  - Download de vídeos sem marca d'água em resolução máxima.
  - Download de carrosséis de fotos inteiros.
  - Suporte a posts de foto única com áudio original sincronizado.
- 📸 **Instagram Reels & Posts**:
  - Resolução direta de vídeos e reels mesmo com áudio protegido/bloqueio de direitos autorais.
  - Carrosséis de fotos e posts individuais em alta definição.
- 🐦 **X (antigo Twitter)**:
  - Extração de vídeos em MP4 em alta qualidade.
  - Download de fotos e galerias múltiplas com nomes de arquivo higienizados.
- 🐾 **Cat Companion Pixel Art**:
  - Gatinho animado integrado no rodapé do aplicativo com 9 animações exclusivas (andar, correr atrás do laser, brincar com novelo, pular, espreguiçar, rolar de barriga, banho de gato, comer peixinho e dormir).
  - **Física de Inclinação Real**: ao angulado o celular para os lados, o gatinho derrapa assustado com as patinhas fincadas e solta fagulhas de poeira!
  - Balões de fala interativos sobre a sprite e controle minimalista expansível.
- 🚀 **Atualizações Automáticas OTA (Over-The-Air)**:
  - Verificação leve via Short Polling no GitHub Releases.
  - Alerta visual no ícone com anel pulsante Neon Green e notificação nativa no sistema operacional Android.
  - Download e instalação automática do APK assinado com apenas um clique.
- 📂 **Organização e Galeria**:
  - Armazenamento físico automático em `Download/Tai/`.
  - Indexação instantânea no `MediaScanner` do Android para visualização imediata na Galeria e reprodutores.

---

## 📱 Redes Sociais Suportadas

| Rede Social | Formatos Suportados | Extração |
| :--- | :--- | :--- |
| **TikTok** | Vídeos sem marca d'água, Carrosséis, Foto com Áudio | API Direta TikWM |
| **Instagram Reels** | Vídeos, Reels com áudio protegido, Fotos, Álbuns | VxInstagram + Embed Context Fallback |
| **X (Twitter)** | Vídeos HD, Imagens isoladas, Carrosséis de fotos | API FxTwitter / Cobalt Multi-endpoint |

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem**: Kotlin 2.0
- **UI Toolkit**: Jetpack Compose com Material 3 (Tema Dark Neon personalizado)
- **Design System**: Cores personalizadas (`NeonCyan`, `NeonGreen`, `PureBlack`, `DarkSurfaceVariant`)
- **Arquitetura**: MVVM com `StateFlow` e Coroutines
- **Rede & Parsing**: OkHttp 4.12, JSONObject
- **Sensores**: Android Hardware `Sensor.TYPE_ACCELEROMETER`
- **Mídia & Armazenamento**: `FileProvider`, `MediaScannerConnection`, `MediaStore`
- **Notificações**: `NotificationChannel` e `NotificationCompat`

---

## 📦 Como Instalar

1. Baixe o APK mais recente na aba de [Releases](https://github.com/Aleff959/Tai/releases/latest).
2. Abra o arquivo `.apk` no seu dispositivo Android.
3. Se solicitado, conceda permissão para instalar aplicativos de fontes desconhecidas para o instalador.
4. Pronto! O app se atualizará automaticamente nas próximas versões via OTA.

---

## 💻 Como Compilar o Projeto

1. Clone o repositório:
   ```bash
   git clone https://github.com/Aleff959/Tai.git
   cd Tai
   ```
2. Abra no Android Studio (Ladybug ou superior) ou compile via Gradle Wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
3. O APK gerado estará em:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 📄 Licença

Distribuído sob a licença MIT. Consulte o arquivo de licença para obter mais informações.
