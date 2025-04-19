package com.github.rxrav.ezcache.core.cmd.impl;

import com.github.rxrav.ezcache.core.Memory;
import com.github.rxrav.ezcache.core.ValueType;
import com.github.rxrav.ezcache.core.ValueWrapper;
import com.github.rxrav.ezcache.core.cmd.Command;
import com.github.rxrav.ezcache.core.error.ValidationError;

public class Ping extends Command {
    @Override
    protected void validate() {
        if (!"PING".equalsIgnoreCase(super.getCmd())) throw new ValidationError("Not correct use of 'ping' command!");
    }
    @Override
    protected ValueWrapper execute(Memory memoryRef) {
        return new ValueWrapper("PONG", ValueType.STRING);
    }
}
