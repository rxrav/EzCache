package com.github.rxrav.ezcache.core.cmd.impl;

import com.github.rxrav.ezcache.core.Memory;
import com.github.rxrav.ezcache.core.ValueType;
import com.github.rxrav.ezcache.core.ValueWrapper;
import com.github.rxrav.ezcache.core.cmd.Command;
import com.github.rxrav.ezcache.core.error.ValidationError;
import com.github.rxrav.ezcache.core.error.WrongTypeError;

import java.util.ArrayList;
import java.util.List;

public class Lrange extends Command {

    private int startIdx = 0;
    private int endIdx = 0;

    @Override
    protected void validate() throws ValidationError {
        if (!"LRANGE".equalsIgnoreCase(super.getCmd())) throw new ValidationError("Not correct use of 'lrange' command!");
        if (super.getArgs().length != 3) throw new ValidationError("Wrong number of arguments for 'lrange' command");
        try {
            this.startIdx = Integer.parseInt(super.getArgs()[1]);
            this.endIdx = Integer.parseInt(super.getArgs()[2]);
        } catch (NumberFormatException e) {
            throw new ValidationError("'lrange' works on a startIdx and endIdx which are numbers");
        }
    }

    @Override
    protected ValueWrapper execute(Memory memoryRef) {
        String key = super.getArgs()[0];

        if (memoryRef.has(key)) {
            ValueWrapper obj = memoryRef.get(key);
            if (obj.getValueType() == ValueType.LIST) {
                List<String> list = ((ArrayList<?>) obj.getValue()).stream().map(String::valueOf).toList();
                ArrayList<String> aList = new ArrayList<>(list);
                if (this.startIdx > aList.size() || this.startIdx > this.endIdx) this.startIdx = 0;
                if (this.endIdx < this.startIdx || this.endIdx > aList.size()) this.endIdx = aList.size();
                return new ValueWrapper(new ArrayList<>(aList.subList(this.startIdx, this.endIdx)), ValueType.LIST);
            }
            throw new WrongTypeError("Key is not mapped to a list");
        } else {
            return new ValueWrapper(new ArrayList<>(), ValueType.LIST);
        }
    }
}
