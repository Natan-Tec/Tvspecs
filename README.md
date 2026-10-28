# TV Specs

App para Android TV no estilo **HWiNFO64**: mostra o hardware da sua TV e os sensores em tempo real, com valores **atual / mínimo / máximo / média**, barras e gráficos.

Feito para TVs fracas (testado em mente para 1 GB de RAM): não usa nenhuma biblioteca externa e **não pede permissão de internet**.

## O que ele mostra

| Painel | Conteúdo |
|---|---|
| **Resumo** | Visão geral com RAM, CPU, rede, temperatura, armazenamento e tela em tempo real |
| **Sistema** | Fabricante, modelo, placa, versão do Android, patch de segurança, kernel, build |
| **Processador** | SoC, tipo dos núcleos (ex.: Cortex-A53), governador e **frequência de cada núcleo** |
| **Memória** | RAM em uso/livre, swap/zRAM, detalhes do `/proc/meminfo` |
| **Armazenamento** | Partições, USB/SD e espaço usado/livre |
| **Tela e GPU** | Resolução, Hz, HDR (Dolby Vision/HDR10/HLG), modos de vídeo, modelo da GPU, OpenGL ES, Vulkan |
| **Rede** | Ethernet/Wi-Fi, IP, sinal, velocidade do link, tráfego ao vivo |
| **Sensores** | Tabela estilo HWiNFO: atual, mín, máx e média de CPU, RAM, rede, Wi-Fi e temperaturas |
| **Codecs e DRM** | H.264, HEVC, VP9, AV1 (hardware ou software), Widevine L1/L3, saídas de áudio HDMI |
| **Recursos** | HDMI-CEC, Bluetooth, USB, controles conectados, todas as features do sistema |
| **Propriedades** | Todas as propriedades do `getprop` |
| **Relatório** | Salva um `.txt` completo na pasta Download |

## TV ou celular (v1.1)

O app detecta sozinho o aparelho:

- **TV:** tela horizontal, menu lateral e controle remoto (como antes).
- **Celular/tablet:** tela sempre na vertical, abas rolando no topo e lista em tela cheia, com toque.

**Próxima atualização:** overlay flutuante (só celular) com temperatura, FPS, uso de CPU, GPU e RAM. A base já está pronta em `OverlayData` e `OverlayController`.

## Como usar no controle remoto

- **◀ ▶** alterna entre o menu e o conteúdo
- **▲ ▼** navega/rola
- **OK** seleciona (nos botões)
- **Voltar** volta ao menu; no menu, sai do app

## Como instalar na TV

Cada vez que o código é enviado ao GitHub, a **Action "Build APK"** compila o app automaticamente.

1. Abra a aba **Actions** do repositório para acompanhar o build, ou
2. Baixe direto o APK mais recente (serve para colar no app **Downloader** da TV):

   `https://github.com/Natan-Tec/Tvspecs/releases/latest/download/TVSpecs.apk`

Depois é só instalar o APK na TV (permitindo "fontes desconhecidas" para o Downloader ou gerenciador de arquivos).

## Limitações (são do Android, não do app)

Muitas TVs bloqueiam para apps comuns:

- **Temperaturas** (`/sys/class/thermal`) — se bloqueado, o painel avisa.
- **Uso total da CPU** (`/proc/stat`) — bloqueado desde o Android 8. O app mostra, no lugar, a frequência de cada núcleo e a CPU usada por ele mesmo.
- **Frequência da CPU** — depende do fabricante liberar a leitura.

O que a TV não libera, o app mostra como indisponível em vez de inventar valores.

## Compilar

O build roda na Action (Java 17 + Gradle 8.9 + Android Gradle Plugin 8.5.2). Para compilar localmente basta abrir a pasta no Android Studio.

Estrutura: `app/src/main/java/com/natantec/tvspecs/`

- `MainActivity` — tela, menu e navegação por controle remoto
- `Pages` — monta o conteúdo de cada painel
- `Sampler` / `SensorStore` — coleta por segundo e guarda mín/máx/média/histórico
- `CodecProbe`, `GpuProbe`, `Info` — consultas pesadas feitas em segundo plano
