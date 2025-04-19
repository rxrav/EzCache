package com.github.rxrav.ezcache.core.cmd.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rxrav.ezcache.core.Constants;
import com.github.rxrav.ezcache.core.Memory;
import com.github.rxrav.ezcache.core.ValueType;
import com.github.rxrav.ezcache.core.ValueWrapper;
import com.github.rxrav.ezcache.core.cmd.Command;
import com.github.rxrav.ezcache.core.error.ValidationError;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Save extends Command {

    @Override
    protected void validate() throws ValidationError {
        if (!"SAVE".equalsIgnoreCase(super.getCmd())) throw new ValidationError("Not correct use of 'save' command!");
        if (super.getArgs().length != 0) throw new ValidationError("Doesn't need additional args, only run 'save'");
    }

    @Override
    protected ValueWrapper execute(Memory memoryRef) {
        String memData;
        String expMetaData;
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            memData = objectMapper.writeValueAsString(memoryRef.getMainMemorySnapshot());
            expMetaData = objectMapper.writeValueAsString(memoryRef.getExpiryMetadataRefSnapshot());
            String dataToSave = memData.concat(Constants.SEPARATOR).concat(expMetaData);
            try (FileOutputStream fos = new FileOutputStream(Constants.DAT_FILE_NAME_AT_CURRENT_PATH)) {
                fos.write(dataToSave.getBytes(StandardCharsets.UTF_8));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new ValueWrapper("OK", ValueType.STRING);
    }
}

