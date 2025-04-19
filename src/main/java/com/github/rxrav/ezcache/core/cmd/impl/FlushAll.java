package com.github.rxrav.ezcache.core.cmd.impl;

import com.github.rxrav.ezcache.core.Memory;
import com.github.rxrav.ezcache.core.ValueType;
import com.github.rxrav.ezcache.core.ValueWrapper;
import com.github.rxrav.ezcache.core.cmd.Command;
import com.github.rxrav.ezcache.core.error.ValidationError;

public class FlushAll extends Command {
    @Override
    protected void validate() throws ValidationError {
        if (!"FLUSHALL".equalsIgnoreCase(super.getCmd())) throw new ValidationError("Not correct use of 'flushall' command!");
        if (super.getArgs().length != 0) throw new ValidationError("Doesn't need additional args, only run 'flushall'");
    }

    @Override
    protected ValueWrapper execute(Memory memoryRef) {
        memoryRef.fullFlush();
        return new ValueWrapper("""
    OK (Note, this doesn't clear any file created using 'save' command.
    After 'flushall', run 'save' to save current memory state""",
                ValueType.STRING);
    }
}
