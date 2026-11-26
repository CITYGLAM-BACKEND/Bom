package com.city.glam.common.code;

/**
 * Response codes shared by every service. Only the numbers live in Java: the text of each code, per screen and per
 * language, is a row in {@code orbit_master_content_key} / {@code orbit_master_translation} (mostly under the screen
 * ORBIT/COMMON/COMMON/COMMON). Service-specific codes live in the service ({@code AuthCodes}, {@code MasterDataCodes}).
 *
 * <p>Ranges: 0xxxx success, 1xxxx field validation, 2xxxx business rules, 3xxxx technical and security errors.
 * Field validation codes are written on the constraint itself: {@code @NotBlank(message = "10201")}.
 */
public final class CommonCodes {

    public static final String SUCCESS = "00001";
    public static final String CREATED = "00002";
    public static final String UPDATED = "00003";
    public static final String DELETED = "00004";
    public static final String FETCHED = "00005";

    /** A constraint without its own code; message args: {0} field, {1} the validator's text. */
    public static final String FIELD_INVALID = "10001";
    public static final String MALFORMED_REQUEST = "10002";

    public static final String DATA_CONFLICT = "20004";

    public static final String INTERNAL_ERROR = "30001";
    public static final String SERVICE_UNAVAILABLE = "30002";
    public static final String UNAUTHORIZED = "30003";
    public static final String ADMIN_LOGIN_REQUIRED = "30004";
    public static final String FORBIDDEN = "30005";
    public static final String ENDPOINT_NOT_FOUND = "30006";
    public static final String METHOD_NOT_ALLOWED = "30007";
    public static final String UNSUPPORTED_MEDIA_TYPE = "30008";
    /** Request body over the limit (same code and text as the gateway's 413). */
    public static final String PAYLOAD_TOO_LARGE = "30012";

    private CommonCodes() {
    }

    /** True for a 5-digit response code such as {@code "10203"}. */
    public static boolean isCode(String value) {
        return value != null && value.length() == 5 && value.chars().allMatch(Character::isDigit);
    }
}
