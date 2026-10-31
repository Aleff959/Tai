# Arquitetura Modular Dinâmica (Hot-Patching)

O **Taí!** implementa uma arquitetura modular de dois estágios inspirada no SwitchCast:
1. **Extratores Nativos Embutidos:** O APK já vem com motores embutidos (NewPipeExtractor, Cobalt, etc.).
2. **Carregador Dinâmico Dex (`ModularExtractorManager`):** Ao iniciar, o app consulta `manifest.json`. Se houver uma versão de módulo (`version > currentVersion`), o app baixa silenciosamente o arquivo `.dex` e o carrega via `DexClassLoader`.

## Como publicar uma correção sem rebuild do APK:
1. Compile a classe que implementa `com.tai.app.network.DynamicMediaExtractorPlugin`.
2. Converta o `.class` / `.jar` em `.dex` usando `d8`:
   ```bash
   d8 --output extractors_core.dex com/tai/dynamic/DynamicExtractorImpl.class
   ```
3. Suba o `extractors_core.dex` para um release do GitHub ou CDN.
4. Atualize o `manifest.json`:
   ```json
   {
     "version": 1,
     "description": "Hotfix para extrator do Instagram/TikTok/YouTube",
     "dexUrl": "https://github.com/Aleff959/Tai/releases/download/v0.0.2_Comp8/extractors_core.dex",
     "updatedAt": "2026-10-31"
   }
   ```
5. O app de todos os usuários baixará a nova lógica instantaneamente no próximo uso sem necessitar reinstalar o APK.
