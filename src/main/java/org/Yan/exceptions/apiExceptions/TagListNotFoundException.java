package org.Yan.exceptions.apiExceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TagListNotFoundException extends RuntimeException {
    public TagListNotFoundException(String ip) {
        super("Nenhuma Tag encontrada no sensor de IP: "+ ip);
    }
}
