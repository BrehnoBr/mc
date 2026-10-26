# Macro Popup (Android)

Popup flutuante que grava toques/deslizes e os reproduz, com repetições (∞) e velocidade.
Funciona sobre qualquer app, via Serviço de Acessibilidade (sem root).

## Gerar o APK

**Opção A – Android Studio (mais simples)**
1. Instale o Android Studio e use *File ▸ Open* nesta pasta.
2. Espere o *Gradle Sync* terminar.
3. *Build ▸ Build APK(s)*. O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.

**Opção B – sem instalar nada (GitHub Actions)**
1. Crie um repositório no GitHub e envie o conteúdo desta pasta.
2. Aba *Actions* ▸ *Build APK* ▸ *Run workflow*.
3. Baixe o artefato `MacroPopup-apk` ao terminar.

## Instalar e ativar
1. Copie o APK para o celular e instale (permita "fontes desconhecidas").
2. Abra o app ▸ "Ativar serviço de acessibilidade" ▸ Macro Popup ▸ ligar.
3. Android 13+: se aparecer "configuração restrita", vá em Configurações do app ▸ menu ⋮ ▸
   "Permitir configurações restritas" e repita o passo 2.

## Uso
- **● Gravar**: toque/deslize na tela (a tela fica levemente avermelhada). **■** para parar.
- **− / +** ajustam repetições (1…100, ∞) e velocidade (0.25x…10x).
- **▶ Reproduzir** / **■ Parar**.
- Arraste o popup pelo cabeçalho; **–** minimiza; **✕** fecha (reabra pelo app).

## Limitações
- Um dedo por vez (sem pinça/multitoque); não grava teclado.
- Ao gravar, o toque é capturado e reenviado ao app logo após levantar o dedo.
  Um segundo toque muito rápido, durante esse reenvio, não é gravado.
- Alguns apps (bancos, DRM) bloqueiam gestos de acessibilidade.
