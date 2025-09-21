package org.Durit.infra;

import com.fazecast.jSerialComm.SerialPort;
import org.Durit.driver.Reader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/leitor")
public class ReaderController {

    @GetMapping("/hello")
    public ResponseEntity<String> hello(){
        return ResponseEntity.ok("Hello World");
    }

    @GetMapping
    public ResponseEntity<?> getAllTagsInRange(){
        SerialPort[] portas;
        portas = SerialPort.getCommPorts();
        SerialPort sensor = portas[1];
        int baud = 115200;
        int hScanner = 0;
        int Address  = 0;

        Reader reader = new Reader();
        if(!reader.open(sensor, baud)){
            return ResponseEntity.badRequest().body("Falha ao conectar com o sensor");
        }
        Set<String> tags = reader.getDistinctTags();
        return ResponseEntity.ok().body(tags);
    }
}
