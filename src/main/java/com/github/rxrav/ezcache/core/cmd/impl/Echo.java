package com.github.rxrav.ezcache.core.cmd.impl;

import com.github.rxrav.ezcache.core.Memory;
import com.github.rxrav.ezcache.core.ValueType;
import com.github.rxrav.ezcache.core.ValueWrapper;
import com.github.rxrav.ezcache.core.cmd.Command;
import com.github.rxrav.ezcache.core.error.ValidationError;

public class Echo extends Command {
    @Override
    protected void validate() {
        if (!"ECHO".equalsIgnoreCase(super.getCmd())) throw new ValidationError("Not correct use of 'echo' command!");
        if (super.getArgs().length != 1) throw new ValidationError("Too many arguments, for multiple tokens wrap them in double quotes");
    }

    @Override
    protected ValueWrapper execute(Memory memoryRef) {
        return new ValueWrapper(super.getArgs()[0], ValueType.STRING);
    }
}
