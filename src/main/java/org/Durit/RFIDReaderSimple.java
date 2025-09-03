package org.Durit;

import com.fazecast.jSerialComm.SerialPort;

import java.util.concurrent.TimeUnit;

public class RFIDReaderSimple {
    private final SerialPort comPort;

    public RFIDReaderSimple(String portaUsb) {
        comPort = SerialPort.getCommPort(portaUsb);
        configurePort();
    }

    private void configurePort( ){
        comPort.setBaudRate(57600);
        comPort.setNumDataBits(8);
        comPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        comPort.setParity(SerialPort.NO_PARITY);
        comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);

    }

    public boolean connect(){
        SerialPort[] portasDisponiveis = SerialPort.getCommPorts();
        if(comPort.openPort()){
            System.out.println("Conectado através da porta: "+comPort.getSystemPortName());
            return true;
        }
        else {
            System.out.println("falha ao conectar na porta, portas disponíveis: ");
            for (SerialPort porta : portasDisponiveis) {
                System.out.println(porta.getSystemPortName());
            }
            return false;
        }
    }

    public void iniciarLeitura(){
        if(!comPort.isOpen()){
            System.err.println("Porta não está aberta");
            return;
        }
        System.out.println("Iniciando leitura de  tags RFID....");

        byte[] readBuffer = new byte[1024];

        while(comPort.isOpen()){
            try{
                int numRead = comPort.readBytes(readBuffer, readBuffer.length);

                if(numRead > 0 ){
                    processRFIDData(readBuffer, numRead);
                }
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (Exception e){
                System.err.println("Erro na leitura: "+ e.getMessage());
                break;
            }
        }
    }

    private void processRFIDData(byte[] data, int length){
        StringBuilder hexString = new StringBuilder();
        for(int i = 0; i < length; i++){
            hexString.append(String.format("%02X ", data[i] & 0xFF));
        }

        String receivedData = hexString.toString().trim();
        System.out.println("Dados: "+ receivedData);
        parseRFIDTag(receivedData);
    }

    private void parseRFIDTag(String hexData){
        if (hexData.length() > 10) {
            System.out.println("Tag RFID detectada!");
            System.out.println("Dados da tag: " + hexData);

            // Exemplo de extração de informações (adapte conforme necessário)
            if (hexData.startsWith("40")) {
                System.out.println("Comando de resposta detectado");
                // Processe conforme o protocolo do seu leitor
            }
        }
    }

    public void disconnect(){
        if (comPort.isOpen()) {
            comPort.closePort();
            System.out.println("Sensor desconectado");
        }
    }
}
