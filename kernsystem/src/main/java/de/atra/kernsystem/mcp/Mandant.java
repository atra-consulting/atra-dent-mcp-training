package de.atra.kernsystem.mcp;

import io.modelcontextprotocol.common.McpTransportContext;

public final class Mandant {

    private static final String WITHOUT_KUNDEN_ID = """
            Dieses Tool arbeitet am Vertrag der aufrufenden Kundin oder \
            des aufrufenden Kunden. Die Kundennummer ist kein Parameter und \
            kann nicht nachgereicht werden: sie kommt aus dem Header \
            x-kunden-id jeder einzelnen Anfrage und muss vom aufrufenden \
            System gesetzt werden. Nachfragen hilft hier nicht weiter -- ohne \
            diesen Header ist keine Akte erreichbar. Ohne Kundenbezug beantwortbar \
            sind tarife_auflisten, tarif_lesen und beitrag_berechnen; die \
            liegen aber nicht in diesem Server, sondern im Rechenkern.""";

    private Mandant() {
    }

    public static long kundenId(McpTransportContext context) {
        Object value = context == null ? null : context.get(McpConfiguration.KUNDEN_ID_KEY);
        if (value instanceof Long kundenId) {
            return kundenId;
        }
        throw new IllegalStateException(WITHOUT_KUNDEN_ID);
    }
}
