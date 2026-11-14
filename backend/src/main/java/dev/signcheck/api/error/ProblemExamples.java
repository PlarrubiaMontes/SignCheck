package dev.signcheck.api.error;

/** JSON examples of the error responses, used in the OpenAPI annotations of the controllers. */
public final class ProblemExamples {

    public static final String NO_FILE = """
            {"type":"about:blank","title":"No file","status":400,\
            "detail":"The 'file' part is required","instance":"/api/v1/validations","code":"NO_FILE"}""";
    public static final String FILE_TOO_LARGE = """
            {"type":"about:blank","title":"File too large","status":413,\
            "detail":"The file exceeds the maximum allowed size of 20480 KB","instance":"/api/v1/validations",\
            "code":"FILE_TOO_LARGE"}""";
    public static final String NO_SIGNATURE_FOUND = """
            {"type":"about:blank","title":"No signature found","status":422,\
            "detail":"The file contains no signature","instance":"/api/v1/validations","code":"NO_SIGNATURE_FOUND"}""";
    public static final String UNSUPPORTED_FORMAT = """
            {"type":"about:blank","title":"Unsupported format","status":422,\
            "detail":"The file is not a supported signed document","instance":"/api/v1/validations",\
            "code":"UNSUPPORTED_FORMAT"}""";
    public static final String VALIDATION_ERROR = """
            {"type":"about:blank","title":"Validation error","status":500,\
            "detail":"The document could not be validated due to an internal error","instance":"/api/v1/validations",\
            "code":"VALIDATION_ERROR"}""";

    private ProblemExamples() {
    }
}
