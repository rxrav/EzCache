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
        int i = 0;
        for (String key: super.getArgs()) {
            ValueWrapper obj = memoryRef.remove(key);
            if (obj != null) ++i;
        }
        return new ValueWrapper(i, ValueType.NUMBER);
    }
}

