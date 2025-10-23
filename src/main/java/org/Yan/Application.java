package org.Yan;

import org.Yan.driver.WirelessReader;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.InputMismatchException;
import java.util.Scanner;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {

        new SpringApplicationBuilder(Application.class)
                .run(args);

    }

    @Bean
    public CommandLineRunner SensorCliRunner() {
        return args -> {
            System.out.println("CLI DO SENSOR ATIVO");
            try (var scanner = new Scanner(System.in)) {

                try {
                    while (true) {

                        System.out.println("Lista de comando \n" + "1 - Obter informações do sensor \n" +
                                "2 - Testar Leitura de Tags \n" +
                                "3 - Configurar Wifi do Sensor \n");
                        int cmd = scanner.nextInt();
                        scanner.nextLine();
                        ;
                        switch (cmd) {
                            case 1 -> TestarConexao(scanner);
                            case 2 -> ListarTags(scanner);
                            default -> {
                                break;
                            }
                        }
                    }
                } catch (InputMismatchException e) {
                    System.out.println("Falha na escrita: Por favor selecione um numero no menu");
                    scanner.nextLine();
                } catch (Exception e) {
                    System.out.println("Erro de conexão!" + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
    }



    private static void ListarTags(Scanner scanner){
        Socket socket = AbrirSocket(scanner);
            var Reader = AbrirSocketSensor(socket);
            if(Reader != null){
                Reader.readParams(1);
            } else {
                throw new NullPointerException("Não foi possível conectar com o sensor");
            }
            var Tags = Reader.Net_getDistinctTags(1);
            if(Tags.isEmpty()){
                System.out.println("Nenhuma Tag detectada");
            }
            Tags.forEach(tag -> {
                System.out.println("ID da Tag: "+ tag);
            });

    }

    private static Socket AbrirSocket(Scanner Scanner){
        try {
            System.out.println("Informe o ip wifi do sensor neste formato: '192.168.2.1' ");
            String IpAdress = Scanner.nextLine().trim();
            System.out.println("Informe a porta wifi do sensor neste formato: '6000' ");
            int PortAdress = Scanner.nextInt();
            Scanner.nextLine();
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(IpAdress, PortAdress), 3000);
            return socket;
        } catch (Exception e) {
            System.out.println("Falha na abertura do socket: " + e.getMessage());
        }
        return new Socket();
    }

    private static void TestarConexao(Scanner scanner){
        Socket socket = AbrirSocket(scanner);
        WirelessReader Reader = AbrirSocketSensor(socket);

            if(Reader != null){
                Reader.readParams(1);
            } else {
                throw new NullPointerException("Não foi possível conectar com o sensor");
            }

    }
    private static WirelessReader AbrirSocketSensor(Socket socket){
        try{
            System.out.println("Conectado com o sensor...");
            return new WirelessReader(socket);
        } catch (Exception e) {
            System.out.println("Falha ao conectar com o Sensor, verifique o ip ou porta do modulo wifi em \n" +
                    "http://<Ip do Sensor>");
            System.out.println("Erro de conexão: "+ e.getMessage());
        }
        return null;
    }

}

