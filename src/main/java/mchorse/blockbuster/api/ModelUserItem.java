package mchorse.blockbuster.api;

import java.util.List;

/**
 * Plain Gson DTO for a {@code user.json} packed-model entry (see P70.1).
 */
public class ModelUserItem
{
    public String obj;
    public String mtl;
    public String vox;
    public List<String> shapes;
}
