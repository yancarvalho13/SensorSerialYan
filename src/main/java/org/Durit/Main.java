package org.Durit;

import com.fazecast.jSerialComm.SerialPort;

import java.util.Arrays;
import java.util.Scanner;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        System.out.println("Portas disponiveis: ");
        SerialPort[] portas = SerialPort.getCommPorts();
        Arrays.stream(portas).forEach(SerialPort::getSystemPortName);

        Scanner scan = new Scanner(System.in);
        System.out.println("Escreva o nome da porta");
        String nomeDaPorta = scan.nextLine();
        System.out.println(nomeDaPorta);

        RFIDReaderSimple reader = new RFIDReaderSimple(nomeDaPorta);

        if(reader.connect()){
            reader.iniciarLeitura();
        }

    }
}