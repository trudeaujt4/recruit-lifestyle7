package com.mz.jarboot.core.event;

import com.mz.jarboot.api.event.JarbootEvent;
import com.mz.jarboot.common.protocol.CommandRequest;
import com.mz.jarboot.common.protocol.CommandType;
import com.mz.jarboot.core.cmd.AbstractCommand;
import com.mz.jarboot.core.cmd.CommandBuilder;
import com.mz.jarboot.core.cmd.internal.AbstractInternalCommand;
import com.mz.jarboot.core.session.AbstractCommandSession;

/**
 * @author majianzheng
 */
public class CommandEventBuilder {
    private CommandRequest request;
    private AbstractCommandSession session;

    public CommandEventBuilder request(CommandRequest request) {
        this.request = request;
        return this;
    }

    public CommandEventBuilder session(AbstractCommandSession session) {
        this.session = session;
        return this;
    }

    public static class CommandEvent implements JarbootEvent {
        protected AbstractCommand command;
        private CommandEvent(AbstractCommand command) {
            this.command = command;
        }

        public AbstractCommand getCommand() {
            return this.command;
        }
    }

    public static class InternalCommandEvent extends CommandEvent {
        private InternalCommandEvent(AbstractCommand command) {
            super(command);
        }
    }

    public JarbootEvent build() {
        AbstractCommand command = CommandBuilder.build(request, session);
        if (command instanceof AbstractInternalCommand && CommandType.INTERNAL.equals(request.getCommandType())) {
            return new InternalCommandEvent(command);
        }
        if (null == command) {
            return null;
        }
        return new CommandEvent(command);
    }
}
