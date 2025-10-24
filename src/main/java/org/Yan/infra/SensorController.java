package org.Yan.infra;

import org.Yan.driver.WirelessReader;
import org.Yan.infra.DTO.SensorDataDTO;
import org.Yan.infra.DTO.SensorResponseDTO;
import org.Yan.infra.DTO.TagDto;
import org.Yan.service.ISensorService;
import org.Yan.service.SensorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@RestController
@RequestMapping("/sensor")
@CrossOrigin("*")
public class SensorController {
    private static final Logger log = LoggerFactory.getLogger(SensorController.class);
    private final ISensorService service;

    public SensorController(ISensorService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<TagDto>> getAllTags( ){
        log.info("Buscando tags...");
        var tags = service.GetAll("192.168.2.1", 6000);
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/{ip}/{porta}/{tagId}")
    public ResponseEntity<TagDto> findById (@PathVariable String ip,@PathVariable int porta,
                                            @PathVariable String tagId){
        var tag = service.GetById(tagId, ip, porta);
        return ResponseEntity.ok(tag);
    }

}
