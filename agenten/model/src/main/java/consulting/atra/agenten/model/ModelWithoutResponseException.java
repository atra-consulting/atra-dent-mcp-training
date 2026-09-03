package consulting.atra.agenten.model;

public class ModelWithoutResponseException extends ModelUnreachableException {

    public ModelWithoutResponseException(String modell) {
        super("Das Modell '" + modell + "' hat nichts geschrieben", modell, null);
    }
}
