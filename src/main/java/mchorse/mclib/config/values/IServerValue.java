package mchorse.mclib.config.values;

/**
 * Port of McLib 2.4.3's {@code config/values/IServerValue.java} (roadmap P19),
 * verbatim.
 */
public interface IServerValue
{
    public void resetServer();

    public boolean parseFromCommand(String value);

    public void copyServer(Value value);
}
