package com.github.rxrav.ezcache.core.cmd.impl;

import com.github.rxrav.ezcache.core.Memory;
import com.github.rxrav.ezcache.core.ValueType;
import com.github.rxrav.ezcache.core.ValueWrapper;
import com.github.rxrav.ezcache.core.cmd.Command;
import com.github.rxrav.ezcache.core.error.ValidationError;

public class Exists extends Command {
    @Override
    protected void validate() throws ValidationError {
        if (!"EXISTS".equalsIgnoreCase(super.getCmd())) throw new ValidationError("Not correct use of 'exists' command!");
        if (super.getArgs().length == 0) throw new ValidationError("Need to pass key(s)");
    }

    @Override
    protected ValueWrapper execute(Memory memoryRef) {
        int i = 0;
        for (String key: super.getArgs()) {
            if (memoryRef.has(key)) ++i;
        }
        return new ValueWrapper(i, ValueType.NUMBER);
    }
}
