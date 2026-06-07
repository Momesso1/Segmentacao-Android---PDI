# Segmentação Semântica de Pets — Treino + App Android

Atividade de segmentação semântica: treinar uma **U-Net** no dataset **Oxford-IIIT Pet**,
exportar para **TensorFlow Lite (FP32)** e rodar a inferência **on-device** num app **Android (Kotlin)**.

Stack escolhida: **TensorFlow/Keras + TensorFlow Lite + Kotlin** (mesma do material da disciplina).
Treinar direto em TF evita a conversão frágil PyTorch → ONNX → TFLite.

```
pet-seg/
├── colab/
│   └── train_segmentation.ipynb   ← treino + métricas + export .tflite
├── android/                        ← projeto Android Studio (Kotlin)
│   └── app/src/main/
│       ├── assets/                 ← coloque model.tflite aqui
│       ├── java/com/example/petseg/MainActivity.kt
│       ├── java/com/example/petseg/Segmenter.kt   ← execução do modelo
│       └── res/layout/activity_main.xml
└── docs/                           ← coloque aqui o print do app funcionando
```

---

## Parte 1 — Treino no Google Colab

1. Abra `colab/train_segmentation.ipynb` no [Google Colab](https://colab.research.google.com/).
2. **Runtime → Alterar tipo de ambiente → GPU** (treino bem mais rápido).
3. **Runtime → Executar tudo.**

O notebook faz, na ordem:

| Etapa da atividade | O que o notebook faz |
|---|---|
| Dataset (recorte reduzido) | Oxford-IIIT Pet via `tfds`, com `take(800)` no treino / `take(200)` no teste |
| Modelo | U-Net compacta (entrada 128×128, 3 classes: pet / fundo / borda) |
| Métricas | **Acurácia** (`evaluate`) e **IoU** (`tf.keras.metrics.MeanIoU`) |
| Visualização | Imagem original, predição e **máscara sobreposta** |
| Export | Converte para **`model.tflite` em FP32** e baixa o arquivo |

> O `MeanIoU` do Keras é o equivalente em TensorFlow ao que a atividade sugere com `torchmetrics`.

Ao final, baixe o `model.tflite` gerado.

---

## Parte 2 — App Android

> O app deixa o usuário **escolher uma imagem da galeria**, mostra a **original** num `ImageView`,
> e ao tocar em **Segmentar** roda o modelo e mostra a **máscara sobreposta** noutro `ImageView`.
> O modelo roda em **FP32**.

### Pré-requisitos
- [Android Studio](https://developer.android.com/studio) instalado.
- Um celular Android com **Depuração USB** ativada (Configurações → Opções do desenvolvedor),
  ou um emulador.

### Caminho recomendado (mais à prova de erro para quem nunca usou Android)

As versões de Gradle/AGP/SDK variam entre instalações. Para evitar erro de sincronização,
a forma mais segura é deixar o Android Studio gerar o esqueleto e só colar os arquivos que importam:

1. **New Project → Empty Views Activity.**
   - Name: `PetSeg`
   - Package name: `com.example.petseg`  *(precisa ser exatamente este)*
   - Language: **Kotlin** · Minimum SDK: **API 24**
2. Espere o primeiro *Gradle Sync* terminar.
3. Copie os arquivos deste repositório por cima dos gerados:
   - `MainActivity.kt`  → `app/src/main/java/com/example/petseg/`
   - `Segmenter.kt`     → `app/src/main/java/com/example/petseg/`
   - `activity_main.xml`→ `app/src/main/res/layout/`
   - `strings.xml`      → `app/src/main/res/values/`
4. Em `app/build.gradle.kts`, dentro do bloco `android { }`, adicione:
   ```kotlin
   androidResources {
       noCompress += "tflite"
   }
   ```
   e em `dependencies { }` adicione:
   ```kotlin
   implementation("androidx.activity:activity-ktx:1.9.2")
   implementation("org.tensorflow:tensorflow-lite:2.16.1")
   ```
   Depois clique em **Sync Now**.
5. Crie a pasta **assets**: clique direito em `app/src/main` → New → Directory → `assets`.
6. Copie o **`model.tflite`** (gerado no Colab) para `app/src/main/assets/model.tflite`.
7. Conecte o celular (ou inicie o emulador) e clique em **Run ▶**.

### Caminho alternativo (abrir o projeto pronto)
Você também pode abrir a pasta `android/` direto no Android Studio
(**Open**). Ele vai baixar o Gradle indicado em `gradle-wrapper.properties` e sincronizar.
Se ele pedir para atualizar o AGP/Gradle para a sua versão, aceite (*Update*).
Lembre-se de colocar o `model.tflite` em `app/src/main/assets/` antes de rodar.

---

## Entregáveis

- [ ] **Print do app funcionando** com a segmentação numa imagem (salve em `docs/`).
- [ ] **Link do repositório** com esta implementação.

### Como gerar o print
1. Rode o app, toque em **Selecionar imagem** e escolha uma foto de um gato ou cachorro.
2. Toque em **Segmentar**.
3. Com a máscara sobreposta visível, tire um screenshot (Power + Volume−).
4. Salve em `docs/` e suba junto no repositório.

---

## Detalhes técnicos (pré-processamento idêntico nos dois lados)

| | Colab (treino) | Android (inferência) |
|---|---|---|
| Tamanho de entrada | 128×128 | 128×128 |
| Normalização | `pixel / 255.0` | `pixel / 255.0` |
| Saída | `[1,128,128,3]` softmax | `argmax` por pixel |
| Classes | 0=pet · 1=fundo · 2=borda | overlay vermelho/azul |

Manter a normalização igual nos dois lados é o que garante que a máscara no celular
fique coerente com a do treino.

---

## Solução de problemas

- **App abre e fecha (crash) ao iniciar**: o `model.tflite` provavelmente não está em
  `app/src/main/assets/`. O nome precisa ser exatamente `model.tflite`.
- **Erro de Gradle Sync sobre versão/JDK**: use o *caminho recomendado* acima
  (criar projeto em branco e colar os arquivos).
- **Máscara sai toda errada**: confirme que a normalização do treino foi `/255.0`
  (é o padrão do notebook) e que a entrada é 128×128.
- **`tensorflow-lite` não encontrado**: confirme `mavenCentral()` em `settings.gradle.kts`
  e clique em *Sync Now*.
