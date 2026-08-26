package com.ruoyi.bi.datasource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class ProcedureSignature {
    private ProcedureSignature() {}

    public static String sha256(List<DatasourceDtos.ProcedureParameter> parameters) {
        String canonical = parameters.stream().map(p -> p.ordinal() + "|" + exact(p.mode()) + "|"
            + exact(p.name()) + "|" + exact(p.mysqlDataType()) + "|" + exact(p.dtdIdentifier()))
            .reduce((a, b) -> a + "\n" + b).orElse("");
        return sha256Text(canonical);
    }

    public static String sha256Text(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String exact(String value) { return value == null ? "" : value.trim(); }
}
