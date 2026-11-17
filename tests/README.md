# Verificações de integração

DeviceChecks.java é uma instrumentação sem dependências externas. Execute somente
em um emulador descartável com Android 11+, nunca em um aparelho com mídias pessoais.

1. Compile e instale a Galeria; permita fotos e vídeos.
2. Configure a tela de teste com `wm size 1080x2340` e `wm density 480`.
3. Em emulador descartável, configure o PIN 1234 (`locksettings set-pin 1234`) para testar a autenticação nativa.
4. Autorize `MANAGE_EXTERNAL_STORAGE` para o teste de operações diretas.
5. Adicione um vídeo com mais de 15 segundos para gestos e busca durante o arraste.
6. Copie tests/fixtures/EditorQA.mp4 para Movies/GaleriaQA/ no emulador e indexe-o. Na API 30, o comando de shell `content call --uri content://media --method scan_file --arg /storage/emulated/0/Movies/GaleriaQA/EditorQA.mp4` solicita a indexação real; o broadcast legado pode deixar linhas com tamanho/duração nulos. Faça o mesmo para o vídeo longo.
7. Execute:

~~~sh
python3 tests/run_device_checks.py --sdk /caminho/android-sdk --serial emulator-5554
~~~

--ecj /caminho/ecj.jar permite compilar com Java Runtime. O teste cria uma foto própria,
restaura reprodução automática e volume e remove suas cópias de imagem ao terminar.
A exportação de vídeo de controle fica em Android/data/app.galeria/files/integration-export.mp4.

Verifica observação do MediaStore, favoritos, busca, zoom, pinça, volume/brilho,
política de rotação, ícones do visualizador, retorno com cache, saltos de vídeo,
busca antes de soltar o dedo, busca oculta no álbum, índice de 10.000 arquivos,
edição real de imagem sem alterar o original, pincéis por caminhos de toque e exportação MP4/H.264 com efeitos e áudio.
Também verifica ícones pintados em alta densidade, editor neutro, destino por nome e reutilização da pasta, cópias com colisão de nomes, movimentação direta de vídeo criado fora do app, exclusão física do álbum, navegação que se recolhe, rolagem parcial em pixels e desbloqueio nativo com PIN.

O resultado fica em app/build/device-checks/<classe>/results.txt. O log atual é
resultado_regressoes_android11_1.3.0.txt. VALIDACAO.md distingue os testes executados dos limites.

Para repetir somente a regressão de sessão/autenticação:

~~~sh
python3 tests/run_device_checks.py --class-name LockChecks --sdk /caminho/android-sdk --serial emulator-5554
~~~

LockChecks configura o PIN 1234 quando não há credencial. Execute exclusivamente no emulador descartável. O log atual é resultado_bloqueio_android11_1.3.0.txt. Os logs com 1.2.0 no nome são históricos.

## Regressões 1.3.0

UpdateChecks testa recuperação da lista do visualizador, retorno do segundo plano, ícones reais de 24dp, pesquisa/sugestões e agrupamento de capturas pelo app, exclusão e restauração com arquivo idêntico, favoritos, colisão de nomes, exclusão permanente e pinça/arraste em vídeo. Gere as mídias com `python3 tests/prepare_update_fixtures.py` (Python e FFmpeg). Copie os arquivos de app/build/update-fixtures para Pictures/GaleriaQA e indexe-os pelo comando scan_file. Requer as imagens Paisagem-A/B/C.png (512×768) e Screenshot_2026-10-05-12-00-00_com.android.settings.png, indexadas em Pictures/GaleriaQA, e MovimentoQA.mp4 com mais de 15s. Execute em ambiente descartável; os arquivos de teste são renomeados e enviados à lixeira/restaurados.

```
python3 tests/run_device_checks.py --class-name UpdateChecks --sdk /caminho/android-sdk
python3 tests/run_device_checks.py --class-name LockChecks --sdk /caminho/android-sdk
python3 tests/run_device_checks.py --class-name ScrollChecks --sdk /caminho/android-sdk
```

ScrollChecks valida o recolhimento/retorno da navegação com gestos nativos na janela em foco e o indicador ao deslocar apenas 3px na mesma linha. Os testes de rolagem esperam o fim do movimento antes de inverter a direção.

A opção --compile-only gera somente o APK de instrumentação. Cada classe usa seu diretório próprio. Os logs finais 1.3.0 distinguem as regressões atuais dos registros históricos 1.2.0. O teste Lens valida o contrato de compartilhamento e a ausência de receptor no emulador; a análise pelo serviço Google não é exercitada sem seus apps instalados.
