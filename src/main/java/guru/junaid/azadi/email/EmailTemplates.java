package guru.junaid.azadi.email;

import guru.junaid.azadi.common.Money;

public final class EmailTemplates {

    private static final String HEADING_STYLE =
        "color:#0c121d;font-family:'Poppins',Arial,sans-serif;font-size:24px;margin:0 0 16px;";
    private static final String BODY_STYLE =
        "color:#0c121d;font-family:'Poppins',Arial,sans-serif;font-size:16px;line-height:1.5;";
    private static final String FOOTNOTE_STYLE =
        "color:#666;font-family:'Poppins',Arial,sans-serif;font-size:14px;line-height:1.5;margin-top:24px;";

    private static final String CONTENT = """
        <h1 style="%s">%s</h1>
        <p style="%s">%s</p>
        <p style="%s">%s</p>
        <p style="%s">%s</p>
        """;

    private static final String LAYOUT = """
        <!DOCTYPE html>
        <html lang="en" xmlns="http://www.w3.org/1999/xhtml" xmlns:v="urn:schemas-microsoft-com:vml">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="x-apple-disable-message-reformatting">
            <!--[if mso]>
            <noscript>
                <xml>
                    <o:OfficeDocumentSettings>
                        <o:PixelsPerInch>96</o:PixelsPerInch>
                    </o:OfficeDocumentSettings>
                </xml>
            </noscript>
            <![endif]-->
            <title>%s</title>
            <link href="https://fonts.googleapis.com/css2?family=Poppins:wght@400;600&display=swap" rel="stylesheet">
        </head>
        <body style="margin:0;padding:0;background-color:#fcfbfc;">
            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"
                   style="background-color:#fcfbfc;">
                <tr>
                    <td align="center" style="padding:40px 20px;">
                        <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0"
                               style="max-width:600px;width:100%%;">
                            <tr>
                                <td style="background-color:#0c121d;padding:24px 32px;text-align:center;">
                                    <h2 style="color:#ffffff;font-family:'Poppins',Arial,sans-serif;font-size:20px;margin:0;">
                                        Azadi Finance
                                    </h2>
                                </td>
                            </tr>
                            <tr>
                                <td style="background-color:#ffffff;padding:32px;">
                                    %s
                                </td>
                            </tr>
                            <tr>
                                <td style="background-color:#f5f5f5;padding:24px 32px;text-align:center;">
                                    <p style="color:#999;font-family:'Poppins',Arial,sans-serif;font-size:12px;margin:0;">
                                        Azadi Finance Portal. This is an automated message, please do not reply directly.
                                    </p>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </body>
        </html>
        """;

    private EmailTemplates() {
    }

    public static String paymentConfirmation(long amountPence) {
        return page("Payment Confirmation", "Payment Confirmed",
            "Your payment of <strong>" + Money.pence(amountPence) + "</strong> has been successfully processed.",
            "This will be reflected in your account within 2-3 business days.",
            "If you did not make this payment, please contact us immediately.");
    }

    public static String bankDetailsUpdated() {
        return page("Bank Details Updated", "Bank Details Updated",
            "Your bank details have been successfully updated on your account.",
            "Future payments will be taken from your new bank account.",
            "If you did not make this change, please contact us immediately.");
    }

    private static String page(String title, String heading, String lead, String detail, String footnote) {
        var content = CONTENT.formatted(
            HEADING_STYLE, heading, BODY_STYLE, lead, BODY_STYLE, detail, FOOTNOTE_STYLE, footnote);
        return LAYOUT.formatted(title, content);
    }
}
