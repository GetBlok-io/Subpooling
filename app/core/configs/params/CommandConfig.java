package io.getblok.subpooling.core.configs.params;

public class CommandConfig {
    private String commandId;
    private String commandAddress;
    private String commandType;
    private long commandValue;

    public CommandConfig(String id, String add, String type, long val){
        commandId = id;
        commandAddress = add;
        commandType = type;
        commandValue = val;
    }

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public String getCommandAddress() {
        return commandAddress;
    }

    public void setCommandAddress(String commandAddress) {
        this.commandAddress = commandAddress;
    }

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }

    public long getCommandValue() {
        return commandValue;
    }

    public void setCommandValue(long commandValue) {
        this.commandValue = commandValue;
    }
}
