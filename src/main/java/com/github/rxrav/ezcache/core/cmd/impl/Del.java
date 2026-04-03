package com.github.rxrav.ezcache.core.cmd.impl;

import com.github.rxrav.ezcache.core.Memory;
import com.github.rxrav.ezcache.core.ValueType;
import com.github.rxrav.ezcache.core.ValueWrapper;
import com.github.rxrav.ezcache.core.cmd.Command;
import com.github.rxrav.ezcache.core.error.ValidationError;

public class Del extends Command {
    @Override
    protected void validate() throws ValidationError {
        if (!"DEL".equalsIgnoreCase(super.getCmd())) throw new ValidationError("Not correct use of 'del' command!");
        if (super.getArgs().length == 0) throw new ValidationError("Need to pass key(s)");
    }

    @Override
    protected ValueWrapper execute(Memory memoryRef) {
        // Single write-lock for all keys — N times fewer lock acquisitions.
        return new ValueWrapper(memoryRef.removeMany(super.getArgs()), ValueType.NUMBER);
    }
}

