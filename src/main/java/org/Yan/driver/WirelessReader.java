    package org.Yan.driver;

    import org.Yan.exceptions.InvalidResponseReaderException;
    import org.Yan.exceptions.NetWorkErrorException;

    import java.io.*;
    import java.net.Socket;
    import java.util.*;
    import java.util.concurrent.ConcurrentHashMap;
    import java.util.concurrent.ExecutorService;
    import java.util.concurrent.Executors;
    import java.util.concurrent.TimeUnit;
    import java.util.concurrent.atomic.AtomicInteger;

    /**
    Esse Driver Para o Sensor Fonkan FI-801 foi desenvolvido por Yan da Silva de Carvalho;
    Engenheiro de Software e Arquiteto de Soluções;
    Contato: yansilva303@gmail.com;
    O driver se baseia em comunicação tcp com o microcontrolador do sensor, através do envio e recebimento
    de pacotes através de arrays de bytes. Para maiores informações consultar o manual de desenvolvimento do
    fabricante
     */

    public class WirelessReader {
        private InputStream inputStream;
        private OutputStream outputStream;
        private Socket socket;
        public static final int _OK          = 0;
        public static final int _comm_error  = 131;
        public static final int _net_error   = 134;
        private static final int MAX_PACKET_LEN = 262;
        private static final int TIMEOUT_MS     = 200000; // TimeOut do legado
        private List<Byte> msg = new ArrayList<>();
        private final AtomicInteger maxDistinct = new AtomicInteger(0);

        private final Set<String> allDistinct = new HashSet<>();
        private final ByteArrayOutputStream btOutGlobal = new ByteArrayOutputStream(4096);
        private volatile boolean running = true;
        private final int intervalMs = 50;    // intervalo entre ciclos


        public WirelessReader(Socket socket) {
            this.socket = socket;
            try {
                this.inputStream = socket.getInputStream();
                this.outputStream = socket.getOutputStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }



        public synchronized void clearBtOut() {
            btOutGlobal.reset();
        }

        /** Retorna uma CÓPIA dos bytes concatenados (thread-safe) */
        public synchronized byte[] getBtOut() {
            return btOutGlobal.toByteArray();
        }


        /**
         *
         * @param hScanner
         * hScanner represente o estado do sensor, passamos int[] hScanner = new int[1]
         * -> dessa forma representamos que o sensor esta conectado, esse metodo e assinado em qualquer comando
         * passado para o sensor;
         * @param wHardVer
         * Aqui é onde recebemos e armazenamos a versao do Hardware do sensor, os valores sao devolvidos em WORDS ->
         * Words sao representados por 16 bits, logo passamos um byte[] wHardVer = new byte[2];
         * @param wSoftVer
         * Aqui é onde recebemos e armazenamos a versao do Software do sensor, os valores sao devolvidos em WORDS ->
         * WORDS são representados por 16 bits, logo passamos um byte[] wSoftVer = new byte[2];
         */
        public void wirelessGetReaderVersion(int hScanner,byte[] wHardVer,byte[] wSoftVer){
            int res = _OK;
            byte[] cmd = new byte[MAX_PACKET_LEN];
            byte[] ret = new byte[MAX_PACKET_LEN];

            try {
                cmd[0] = 2;
                cmd[1] = 2;
                cmd[2] = 0;
    //        WirelessRunner();

                if (!wirelessTcpPacket(hScanner, cmd, ret)) {
                    throw new NetWorkErrorException("Falha ao enviar pacote TCP");
                };

                if(ret[0] == (byte)0xF4){
                    throw new InvalidResponseReaderException("Falha ao obter Versão do Leitor");
                }

                System.arraycopy(ret, 2, wHardVer, 0, 2);
                System.arraycopy(ret, 4, wSoftVer, 0,2 );


            } catch (Exception e){
                e.printStackTrace();
            }

        }



        private boolean wirelessTcpPacket(int hScanner, byte[] cmd, byte[] res) {
            try {
                if (inputStream == null || outputStream == null) return false;

                /*Aqui montamos o pacote de envio para o sensor, criamos um array com 2 bytes
                no primeiro cmd[0] acomodamos o start 0x40 (padrão de todos os comandos) e deixamos
                um espaço vazio para o checksum (soma de todos os bytes do pacote) do final.
                 */
                byte[] send = new byte[(cmd[0] & 0xFF) + 2];
                byte sum = 0x40;
                send[0] = 0x40; // -> armazenamento do start 0x40
                for (int i = 0; i < (cmd[0] & 0xFF); i++) {
                    /*
                    aqui armazenamos os bytes do comando começando da pos 1 do pacote send
                    e fazemos a soma começando de 0x40 ate o ultimo byte do comando para obter
                    o checksum.
                     */
                    send[i+1] = cmd[i]; sum += send[i+1];
                }



                sum = (byte)(~sum + 1);
                send[send.length - 1] = sum;

                // envia
                outputStream.write(send);
                outputStream.flush();


                long deadline = System.currentTimeMillis() + TIMEOUT_MS;
                /*
                Aqui vamos ler os 4 bytes do inicio do pacote de resposta do sensor
                em head4[0] podemos obter 0xF0 (Sucesso) ou 0xF4 (erro);
                Em head4[1] temos o lenght da resposta, que inclui, o eco do comando head4[2]
                ,o dispositivo ecoa o comando para vc validar que aquela resposta e referente
                ao comando que vc enviou. E em seguida o payload head4[3] -> primeiro byte do
                payload;
                 */
                byte[] head4 = readExact(inputStream, 4, deadline);
                byte boot = head4[0];
                int  len  = head4[1] & 0xFF;
                /*
                Rest verifica e obtem o restante do payload que ainda falta ser lido
                a parti do len - 2 ( 1 é o cmdEco e 1 é o primeiro byte do payload)
                 */
                int  rest = Math.max(0, len - 2); // bytes restantes após [len][cmdEco]

                /*
                Faz a leitura dos bytes restantes do payload da resposta do sensor
                e armazena os valores em byte[] body
                 */
                byte[] body = (rest > 0) ? readExact(inputStream, rest, deadline) : new byte[0];

                // montar receiveTemp “virtual” para reaproveitar a lógica:
                byte[] rx = new byte[4 + body.length];
                System.arraycopy(head4, 0, rx, 0, 4);
                if (body.length > 0) System.arraycopy(body, 0, rx, 4, body.length);

                // valida eco
                if (rx[2] != cmd[1]) return false;               // cmd ecoado deve bater

                if (boot == (byte)0xF0) {                        // sucesso
                    res[0] = (byte)0xF0;
                    int dataLen = Math.max(0, len - 1);
                    int copy = Math.min(dataLen, res.length - 1);
                    if (copy > 0) System.arraycopy(rx, 3, res, 1, copy);
                    return true;
                } else if (boot == (byte)0xF4) {                 // erro
                    res[0] = (byte)0xF4;
                    res[1] = (len >= 2) ? rx[3] : 0;
                    return true;
                }
                return false;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }


        public int Net_SetAntenna(int hScanner, int Antenna) {
            int res = _OK;
            byte[] put = new byte[MAX_PACKET_LEN];
            byte[] get = new byte[MAX_PACKET_LEN];

            try {
                put[0] = 3;
                put[1] = 10;
                put[2] = (byte)Antenna;
                put[3] = 0;
                if (!wirelessTcpPacket(hScanner, put, get)) {
                    return _net_error;
                }

                if (get[0] == -12) {
                    return get[1];
                }

                res = _OK;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception of Net_SetAntenna:\n");
                res = -1;
            }
            return res;
        }

        /**
         *
         * @param in
         * Stream de dados do pacote resposta do Sensor
         * @param restBytes
         * Quantidade de bytes que faltam para o final da leitura
         * @param deadlineMs
         * Tempo Maximo de leitura para timeout
         * @return
         * Um array de bytes com os dados enviados pelo sensor
         * @throws IOException
         * Exceção de Timeout por tempo de leitura
         */
        private static byte[] readExact(InputStream in, int restBytes, long deadlineMs) throws IOException {
            byte[] out = new byte[restBytes];
            int initialPosition = 0;
            while (initialPosition < restBytes) {
                if (System.currentTimeMillis() > deadlineMs)
                    throw new IOException("Serial read timeout (" + restBytes + " bytes)");
                /*
                readedByes, representa quantos bytes fora lidos na chamada de in.read.
                Logo in.read faz a leitura de todos os bytes no stream de dados, e copia para
                o array out.
                 */
                int readedBytes = in.read(out, initialPosition, restBytes - initialPosition);
                if (readedBytes < 0) throw new EOFException("Serial closed");
                if (readedBytes == 0) {
                    try { Thread.sleep(1); } catch (InterruptedException ignored) {}
                    continue;
                }
                initialPosition += readedBytes;
            }
            return out;
        }

        public Set<String> Net_getDistinctTags(int hScanner) {
            try { Net_AutoMode(hScanner,0,0); Net_SetAntenna(hScanner, 1);} catch (Throwable ignore) {}
            var starttime = System.currentTimeMillis();

            // parâmetros fixos para leitura de EPC sem máscara
            final int mem = 1, ptr = 0, len = 0;
            final byte[] mask = new byte[0];

            // buffers de trabalho
            final byte[] firstBuf = new byte[7096]; // EEh 1ª página
            final int[]  nCounter = new int[1];     // total de grupos (tags) reportados
            final int[]  IDlen    = new int[1];     // bytes válidos da 1ª página

            // loop contínuo como no legado
            var finalTime = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < finalTime ) {
                try {
                    // -------- 1) Seleção de antena (igual ao legado) ----------


                    // -------- 2) Leitura paginada (EEh + EDh) ----------
                    int r;

                        // NET
                        r = Net_EPC1G2_ListTagID(hScanner, mem, ptr, len, mask, firstBuf, nCounter, IDlen);

                    if (r != _OK) {
                        sleepQuiet(intervalMs);
                        continue;
                    }

                    // parse da 1ª página
                    int got = parseListBuffer(firstBuf, IDlen[0], allDistinct);

                    // se houver mais, pagina com EDh
                    int total = nCounter[0]; // -> Total de tags lidas em ListTagId ; Ex: 50 tags
                    int start = got;
                    int left  = Math.max(0, total - got); // -> total - a primeira leitura (8)

                    final int ED_PAGE = 8; // EDh retorna até 10 grupos por chamada
                    while (left > 0) {
                        int fetch = Math.min(ED_PAGE, left);
                        byte[] pageBuf = new byte[1024];
                        int[]  pageLen = new int[1];

                            /*Aqui buscamos as tags baseado na posicao dela na memoria; 8 / 16 / 32 ->
                            cada pagina contem 8 tags; o numero total de paginas e pego em ListTagID;
                             */
                            r = Net_EPC1G2_GetIDList(hScanner, pageBuf, start, fetch, pageLen);

                        if (r != _OK) break;

                        parseListBuffer(pageBuf, pageLen[0], allDistinct);
                        start += fetch;
                        left  -= fetch;
                    }

                    // -------- 3) Console: imprime quando bater novo máximo ----------
                    int sz = allDistinct.size();
                    // só loga quando houver crescimento
                    int prev = maxDistinct.get();
                    if (sz > prev && maxDistinct.compareAndSet(prev, sz)) {
                        System.out.println("[MyTimer] Máximo histórico de tags únicas: " + sz);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    sleepQuiet(intervalMs);
                }
            }
            return allDistinct;
        }

        private static int parseListBuffer(byte[] buf, int usedLen, Set<String> out) {
            int i = 0, groups = 0;
            byte[] temp = new byte[128]; // EPC máx 32 words = 64 bytes → 128 cabe

            while (i < usedLen) {
                int L = buf[i] & 0xFF;   // comprimento em WORDS
                if (L == 0 || L > 32) break; // sanity check

                int epcBytes = L * 2;
                int need = 1 + epcBytes; // 1 (len) + EPC
                if (i + need > usedLen) break; // buffer truncado

                // copia o ID da tag comecando de buf[L+1] ate buf[L*2]
                System.arraycopy(buf, i + 1, temp, 0, epcBytes);

                //extrai a tag passando o array com o ID e o tamanho do ID
                String epcHex = bytes2HexString(temp, epcBytes);

                //Adiciona a Tag a Lista de Tags
                out.add(epcHex);

                i += need;
                groups++;
            }
            return groups;
        }

        // util do usuário
        public static String bytes2HexString(byte[] b, int count) {
            String ret = "";
            for (int i = 0; i < count; ++i) {
                String hex = Integer.toHexString(b[i] & 0xFF);
                if (hex.length() == 1) hex = '0' + hex;
                ret += hex.toUpperCase();
            }
            return ret;
        }

        private static void sleepQuiet(int ms) {
            try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
        }




        public int Net_AutoMode(int hScanner, int Mode, int Address) {
            int res = 0;
            byte[] cmd = new byte[MAX_PACKET_LEN];
            byte[] ret = new byte[MAX_PACKET_LEN];
            try {
                if (Address == 0) { cmd[0]=3; cmd[1]=15; cmd[2]=(byte)Mode; cmd[3]=0; }
                else              { cmd[0]=4; cmd[1]=15; cmd[2]=(byte)Address; cmd[3]=(byte)Mode; cmd[4]=0; }

                if (!wirelessTcpPacket(hScanner, cmd, ret)) return _net_error;
                if (ret[0] == (byte)0xF4) return (ret[1] & 0xFF); // erro
                return _OK;
            } catch (Exception e) {
                e.printStackTrace(); res = -1;
            }
            return res;
        }

        // Agregador — chama EEh e, se M>8, pagina com EDh até completar.
        private  int Net_EPC1G2_ListTagID(int hScanner, int mem, int ptr, int len, byte[] mask, byte[] btID, int[] nCounter, int[] IDlen) {
            int res = _OK;
            byte[] put = new byte[MAX_PACKET_LEN];
            byte[] get = new byte[7680];

            try {
                int m;
                if (len == 0) {
                    m = 0;
                } else {
                    m = len / 8;
                    if (len % 8 != 0) {
                        ++m;
                    }
                }

                put[0] = (byte)(m + 6);
                put[1] = -18;
                put[2] = (byte)mem;
                put[3] = (byte)(ptr >> 8);
                put[4] = (byte)ptr;
                put[5] = (byte)len;

                int i;
                for(i = 0; i < m; ++i) {
                    put[6 + i] = mask[i];
                }

                put[6 + i] = 0;
                if (!wirelessTcpPacket(hScanner, put, get)) {
                    return _net_error;
                }

                if (get[0] == -12) {
                    return get[1];
                }

                nCounter[0] = get[1]; // -> Me devolve o total de tags Lidas, maximo de 8
                int last = 2;
                if (nCounter[0] <= 8) {
                    for(int var19 = 0; var19 < nCounter[0]; ++var19) {
                        // L em WORDS, unsigned -> Começa a Ler a partir da pos 2 de get -> get[2] = primeiro L
                        int L = get[last] & 0xFF; // -> primeiro L


                        /*
                        Se L = 0 -> sem tag , e se L > 32 ou seja a tag nao pode ter mais que 64 bytes
                        * */
                        if (L == 0 || L > 32) return _comm_error;  // sanity opcional

                        /*
                        Aqui obtemos o ID da Tag -> o id começa apos a pos 2(L) e vai ate L*2
                        por que L indica o length da tag em WORDS ou seja 2*L -> se L é 6 entao a tag tem 12 bytes
                        e vai de get[L + 1] até get[L + 1 + L*2 - 1];

                        se melhor exemplificado:
                        L = 8 -> entao o id da tag tem 16 bytes;
                        Se o ID da tag inicia apos L e L está em get[2] a tag inicia em get[3]
                        get[3] -> e o byte0 da tag, que vai até o byte15 -> Logo até get[18] (3+15);

                        next -> representa o L da próxima tag que no caso esta apos L + TAGID;
                        * */
                        int next = L * 2 + 1;

                        if (last + next > get.length) return _comm_error;

                        /*
                        Aqui copiamos o array get[], começano de last(L), para o array btID, começando de
                        L - 2 = 0, ate next que é L * 2 + 1; Ex: se L fosse 8 -> 16 + 1 -> ou seja
                        vai copiar de get[2] ate get[18]
                        * */
                        System.arraycopy(get, last, btID, last - 2, next);

                        // Last passa a ser L + next -> L + a tag inteira + 1  = proximo L
                        last += next;
                    }
                } else {
                    for(int var18 = 0; var18 < 8; ++var18) {
                        int L = get[last] & 0xFF;           // L em WORDS, unsigned
                        if (L == 0 || L > 32) return _comm_error;  // sanity opcional
                        int next = L * 2 + 1;

// e ANTES do arraycopy:
                        if (last + next > get.length) return _comm_error;
                        System.arraycopy(get, last, btID, last - 2, next);
                        last += next;
                    }
                }

                IDlen[0] = last - 2;
                res = _OK;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception of Net_EPC1G2_ListTagID:\n");
                res = -1;
            }

            return res;
        }

        private  int Net_EPC1G2_GetIDList(int hScanner, byte[] btID, int stNum, int nCounter, int[] IDlen) {
            int res = _OK;
            byte[] put = new byte[MAX_PACKET_LEN];
            byte[] get = new byte[7680];

            try {
                put[0] = 4;
                put[1] = -19;
                put[2] = (byte)stNum; // -> indice do proximo grupo de tags no caso 8 por que ja rodamos ListTagID
                put[3] = (byte)nCounter; // quantos grupos de pagina queremos na chamada
                put[4] = 0;
                if (!wirelessTcpPacket(hScanner, put, get)) {
                    return _net_error;
                }

                if (get[0] == -12) {
                    return get[1];
                }

                int last = 2;


                for (int i = 0; i < nCounter; ++i) {
                    if (last >= get.length) return _comm_error;
                    int L = get[last] & 0xFF;
                    if (L == 0 || L > 32) return _comm_error;
                    int next = L * 2 + 1;
                    if (last + next > get.length || last - 2 + next > btID.length) return _comm_error;

                    System.arraycopy(get, last, btID, last - 2, next);
                    last += next;
                }
                IDlen[0] = last - 2;

                res = _OK;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception of Net_EPC1G2_GetIDList:\n");
                res = -1;
            }

            return res;
        }

        public  int Net_EPC1G2_ReadLabelID(int hScanner, int mem, int ptr, int len, byte[] mask, byte[] IDBuffer, int[] nCounter) {
            int res = _OK;

            try {
                int i = 0;
                int j = 0;
                int k = 0;
                int[] l = new int[2];
                int[] IDlen = new int[2];
                byte[] lpbuffer = new byte[1024];
                nCounter[0] = 0;
                res = Net_EPC1G2_ListTagID(hScanner, mem, ptr, len, mask, IDBuffer, nCounter, IDlen);
                if (res != _OK) {
                    return res;
                }

                if (nCounter[0] > 8) {
                    j = nCounter[0] / 8;
                    k = nCounter[0] - j * 8;

                    for(int e = 1; e < j; ++e) {
                        res = Net_EPC1G2_GetIDList(hScanner, lpbuffer, e * 8, 8, l);
                        if (res != _OK) {
                            return res;
                        }

                        IDlen[0] += l[0];
                    }

                    if (k != 0) {
                        res = Net_EPC1G2_GetIDList(hScanner, lpbuffer, j * 8, k, l);
                        if (res != _OK) {
                            return res;
                        }
                    }
                }

                res = _OK;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception of Net_EPC1G2_ReadLabelID:\n");
                res = -1;
            }

            return res;
        }
    /**
     * Configura o intervalo de leitura no modo automático
     * @param hScanner identificador do scanner (geralmente 0)
     * @param intervalValue valor do intervalo:
     *                     0 = 10ms
     *                     1 = 20ms
     *                     2 = 30ms (padrão)
     *                     3 = 50ms
     *                     4 = 100ms
     * @return código de resultado (0 = sucesso)
     */
    public int setReadInterval(int hScanner, int intervalValue) {
        if (intervalValue < 0 || intervalValue > 4) {
            System.out.println("Valor de intervalo inválido. Use: 0=10ms, 1=20ms, 2=30ms, 3=50ms, 3=100ms");
            return -1;
        }

        int Address = 0;  // Endereço padrão
        byte[] autoParams = new byte[32];

        // 1. Primeiro lemos os parâmetros atuais
        byte[] cmd = new byte[MAX_PACKET_LEN];
        byte[] ret = new byte[MAX_PACKET_LEN];

        // Comando para ler AutoParam
        cmd[0] = 2;
        cmd[1] = 20;  // Comando 0x14 (20) = ReadAutoParam
        cmd[2] = 0;

        if (!wirelessTcpPacket(hScanner, cmd, ret)) {
            System.out.println("Falha ao ler parâmetros automáticos");
            return _net_error;
        }

        if (ret[0] == (byte)0xF4) {
            System.out.println("Erro ao ler parâmetros: " + (ret[1] & 0xFF));
            return (ret[1] & 0xFF);
        }

        // Copiar os parâmetros recebidos
        System.arraycopy(ret, 1, autoParams, 0, 32);

        // 2. Modificar o byte 4 (índice 3) que controla o intervalo de leitura
        autoParams[3] = (byte)intervalValue;

        // 3. Escrever os parâmetros de volta
        cmd = new byte[MAX_PACKET_LEN];
        ret = new byte[MAX_PACKET_LEN];

        cmd[0] = (byte)(2 + 32);  // Length = 2 + 32 bytes de parâmetros
        cmd[1] = 19;  // Comando 0x13 (19) = WriteAutoParam
        System.arraycopy(autoParams, 0, cmd, 2, 32);
        cmd[2 + 32] = 0;

        if (!wirelessTcpPacket(hScanner, cmd, ret)) {
            System.out.println("Falha ao escrever parâmetros automáticos");
            return _net_error;
        }

        if (ret[0] == (byte)0xF4) {
            System.out.println("Erro ao escrever parâmetros: " + (ret[1] & 0xFF));
            return (ret[1] & 0xFF);
        }

        System.out.println("Intervalo de leitura configurado com sucesso para: " +
                          (intervalValue == 0 ? "10ms" :
                           intervalValue == 1 ? "20ms" :
                           intervalValue == 2 ? "30ms" :
                           intervalValue == 3 ? "50ms" : "100ms"));

        return _OK;
    }

        /**
         * Método que lê múltiplos lotes de tags e usa multithreading para processamento
         */
        public Set<String> Net_getDistinctTagsBatchedMultithread(int numBatches) {
            final int hScanner = 0, Address = 0;
            final ConcurrentHashMap<String, Boolean> allTags = new ConcurrentHashMap<>();
            final ExecutorService executor = Executors.newFixedThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors() - 1)
            );

            try {
                // Garantir modo correto
                Net_AutoMode(hScanner, 0, Address);

                // Ler em vários lotes
                for (int batch = 0; batch < numBatches; batch++) {
                    byte[] IDBuffer = new byte[4096];
                    int[] nCounter = new int[1];

                    // Limpar o buffer
                    Arrays.fill(IDBuffer, (byte)0);

                    // Leitura
                    int r = Net_EPC1G2_ReadLabelID(hScanner, 1, 0, 0, new byte[0], IDBuffer, nCounter);

                    if (r == _OK && nCounter[0] > 0) {
                        System.out.println("Lote " + (batch + 1) + ": Detectadas " + nCounter[0] + " tags");

                        // Criar uma cópia do buffer para processamento assíncrono
                        final byte[] bufferCopy = Arrays.copyOf(IDBuffer, IDBuffer.length);
                        final int tagCount = nCounter[0];

                        // Submeter o lote para processamento em segundo plano
                        executor.submit(() -> {
                            // Processar as tags deste lote
                            processTagsToMap(bufferCopy, tagCount, allTags);
                        });

                        // Esperar um pouco entre lotes para evitar sobrecarregar o dispositivo
                        Thread.sleep(500);
                    } else {
                        // Se não encontrou tags neste lote, não precisa continuar
                        if (batch > 0) break;
                    }
                }

                // Aguardar todas as threads concluírem
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (executor != null && !executor.isShutdown()) {
                    executor.shutdownNow();
                }
            }

            return new HashSet<>(allTags.keySet());
        }

        /**
         * Processa as tags de um buffer e adiciona ao mapa concorrente
         */
        private void processTagsToMap(byte[] buffer, int numTags, ConcurrentHashMap<String, Boolean> tagsMap) {
            int idx = 0;
            int tagsProcessed = 0;

            while (idx < buffer.length && tagsProcessed < numTags) {
                if (buffer[idx] == 0) break;

                int L = buffer[idx] & 0xFF;

                if (L < 1 || L > 31 || idx + 1 + L * 2 > buffer.length) {
                    idx++;
                    continue;
                }

                StringBuilder epc = new StringBuilder(L * 4);
                for (int k = 0; k < L * 2; k++) {
                    epc.append(String.format("%02X", buffer[idx + 1  + k]));
                }

                char a = 'E';
                char b = '2';
                String tag = epc.toString();
                System.out.println("Tag: "+ tag);
                if (!tag.isEmpty() && !tag.contains("000000000000")) {

                        tagsMap.put(tag, true);

                }

                idx += 1 + L * 2;
                tagsProcessed++;
            }
        }


        /**
         * Lê o bloco de 32 bytes de parâmetros básicos de operação do leitor.
         *
         * @param hScanner O identificador do scanner (geralmente 0).
         * @return Um array de 32 bytes com os parâmetros em caso de sucesso, ou null em caso de falha.
         */
        public byte[] readParams(int hScanner) {
            byte[] cmd = new byte[MAX_PACKET_LEN];
            byte[] ret = new byte[MAX_PACKET_LEN];

            try {
                // Montando o comando 06H (Read Param)
                // Baseado no manual e no seu código existente: [comprimento], [comando], [terminador nulo]
                cmd[0] = 2;       // Comprimento do payload (comando + terminador)
                cmd[1] = 0x06;    // Comando 06H: Read Param
                cmd[2] = 0;

                if (!wirelessTcpPacket(hScanner, cmd, ret)) {
                    System.out.println("Falha na comunicação ao tentar ler os parâmetros.");
                    return null; // Retorna nulo em caso de erro de rede/comunicação
                }

                // Verifica se o dispositivo retornou um código de erro
                if (ret[0] == (byte)0xF4) {
                    System.out.println("O leitor retornou um erro ao ler parâmetros: " + (ret[1] & 0xFF));
                    return null; // Retorna nulo em caso de erro do dispositivo
                }

                // Se chegamos aqui, a operação foi um sucesso (ret[0] == 0xF0)
                // O manual diz que a resposta contém 32 bytes de dados.
                byte[] params = new byte[32];

                // Os dados começam no segundo byte da resposta (`ret[1]`),
                // pois o primeiro é o status `F0H`.
                System.arraycopy(ret, 1, params, 0, 32);

                ParseParams(params);

                return params;

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        private void ParseParams(byte[] parametros){
            Map<Integer, Integer> BaudRates = Map.of(4,9600,
                    5,19200,
                    6,38400,
                    7,57600,
                    8,115200);
            Map<Integer, String> TagTypes = Map.of(1,"ISO18000-6B",
                    2,"EPCC1",
                    4,"EPCC1G2",
                    8,"EM4442",
                    10,"ATA");
            Map<Integer, String> ReadingDurationTimes = Map.of(0, "10ms",
                    1,"20ms",
                    2,"30ms",
                    3,"40ms");

            int hScanner = 1;
            byte[] ip = new byte [4];
            byte[] port = new byte[2];
            byte[] mask = new byte[4];
            byte[] gateway = new byte[4];
            byte[] macAdress = new byte[6];
            int BaudRate = parametros[0] & 0xFF;
            int TransmissionPower = parametros[1] & 0xFF;
            int ReaderMode = parametros[5] & 0xFF;
            int TagType = parametros[8] & 0xFF;
            int ReadinDuration = parametros[9] & 0xFF;

            System.out.println("Sensor conectado com sucesso");
            System.out.println("================DADOS DO SENSOR===================");

            System.out.println("Baud Rate da porta Serial: "+ BaudRates.get(BaudRate)+"bps");

            System.out.println("Potencia de transmissão: "+ TransmissionPower);

            StringJoiner sj = new StringJoiner(" ~ ");
            sj.add(String.valueOf(parametros[2] & 0xFF));
            sj.add(String.valueOf(parametros[3] & 0xFF));
            System.out.println("Ponto inicial - final da frequência: "+sj );

            System.out.println("Profundidade de modulação: "+ (parametros[4] & 0xFF));

            System.out.println("Modo ativo do sensor: " + (ReaderMode == 0 ? "Auto - 0 " : "Command - 1"));

            System.out.println("Máximo de Tags por leitura: " + (parametros[8] & 0xFF));

            System.out.println("Tipo da Tag Configurada : " + TagTypes.get(TagType));

            System.out.println("Duração do tempo de leitura da tag: " + ReadingDurationTimes.get(ReadinDuration));

            System.out.println("Quantidade de Leituras por requisição: " + (parametros[10] & 0xFF));

            System.out.println("Alarme (buzzer) de leitura ligado :" + ((parametros[11] & 0xFF) == 0 ? "Desligado - 0" : "Ligado - 1"));

            System.arraycopy(parametros,12, ip, 0, 4);
            sj = new StringJoiner(".");
            for (byte b : ip){
                sj.add(String.valueOf((b & 0xFF)));
            }
            System.out.println("Endereço ip: "+ sj);

            System.arraycopy(parametros, 16, port, 0, 2);
            StringBuilder sb = new StringBuilder();
            for (byte b : port) {
                sb.append(b & 0xFF);
            }
            System.out.println("Porta: " + sb);

            System.arraycopy(parametros, 18, mask, 0, 4);
            sj = new StringJoiner(".");
            for (byte b : mask) {
                sj.add(String.valueOf(b & 0xFF));
            }
            System.out.println("Mascara: "+ sj);

            sj = new StringJoiner(".");
            System.arraycopy(parametros, 22,gateway, 0, 4);
            for(byte b : gateway){
                sj.add(String.valueOf(b & 0xFF));
            }
            System.out.println("Gateway: " + sj);

            sj = new StringJoiner(":");
            System.arraycopy(parametros, 26, macAdress, 0, 6);
            for (byte b : macAdress){
                sj.add(String.format("%02X",b & 0xFF));
            }
            System.out.println("Mac: "+sj);



        }

        /**
         * Imprime os parâmetros de operação de forma legível, com base no array de 32 bytes.
         * @param params O array de 32 bytes retornado pela função readParams.
         */
        public void printParams(byte[] params) {
            if (params == null || params.length < 32) {
                System.out.println("Array de parâmetros inválido ou nulo.");
                return;
            }

            System.out.println("--- Parâmetros Atuais do Leitor ---");
            // Usamos '& 0xFF' para garantir que o valor do byte seja exibido como um número de 0 a 255.

            // O manual usa base 1, nosso array usa base 0. Ex: Byte 1 é índice 0.
            System.out.println("Byte  1 (Índice  0) - BaudRate: " + (params[0] & 0xFF));
            System.out.println("Byte  2 (Índice  1) - Potência de Transmissão (0-63): " + (params[1] & 0xFF));
            System.out.println("Byte  6 (Índice  5) - Modo de Trabalho (0=Auto, 1=Comando): " + (params[5] & 0xFF));
            System.out.println("Byte  7 (Índice  6) - Endereço RS485: " + (params[6] & 0xFF));
            System.out.println("Byte  8 (Índice  7) - Máx. Tags por Leitura: " + (params[7] & 0xFF));
            System.out.println("Byte  9 (Índice  8) - Tipo de Tag (4=G2): " + (params[8] & 0xFF));
            System.out.println("Byte 10 (Índice 9) - Duração da leitura da tag: " + (params[9 & 0xFF]));
            System.out.println("Byte 11 (Índice 10) - Read Times (M): " + (params[10] & 0xFF));
            System.out.println("Byte 12 (Índice 11) - Buzzer Ativado (1=Sim, 0=Não): " + (params[11] & 0xFF));

            // Exemplo de como ler um parâmetro de múltiplos bytes (IP)
            String ipAddress = (params[12] & 0xFF) + "." + (params[13] & 0xFF) + "." + (params[14] & 0xFF) + "." + (params[15] & 0xFF);
            System.out.println("Bytes 13-16 (Índices 12-15) - Endereço IP: " + ipAddress);

            System.out.println("------------------------------------");
        }
    }
