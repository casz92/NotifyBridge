# Skill: NotifyBridge Rule Import & Export File Specification

This guide details the structure, versioning, fields, and variables required to build or modify rules import files for **NotifyBridge**.

---

## JSON Format & Versioning

NotifyBridge supports a single versioned JSON format for importing:

### Version 1 (Standard Format)
The root element is a JSON Object which includes metadata such as the file version (value `1`) and the array of rules.

```json
{
  "version": 1,
  "rules": [
    {
      "name": "App Notification Forwarder",
      "source": "APP",
      "appPackageNames": "com.whatsapp,com.telegram.messenger",
      "regexPattern": ".*OTP.*",
      "regexMatchFields": "title,text",
      "httpUrl": "https://api.myweb.com/otp",
      "httpMethod": "POST",
      "headers": [
        { "header": "Content-Type", "value": "application/json" },
        { "header": "Authorization", "value": "Bearer {global_API_KEY}" }
      ],
      "bodyTemplate": "{\"app\":\"{package_name}\",\"message\":\"{not_text}\"}",
      "_vars": [
        { "name": "API_KEY", "value": "super_secret_token" }
      ]
    }
  ]
}
```

---

## Fields Reference

Each rule object within the `"rules"` array supports the following attributes:

| Field Name | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `name` | String | Yes | Name of the rule. Defaults to `regla_importada`. |
| `source` | String | Yes | Event source. Supported values: `APP`, `SMS`, `IMAP`. |
| `appPackageNames` | String / null | No | Comma-separated list of Android application package IDs (e.g. `com.whatsapp,com.android.settings`). Use `*` to match all applications. Set to `null` if the source is `SMS` or `IMAP`. |
| `regexPattern` | String | Yes | Regular expression to match. Defaults to `.*` (match everything). |
| `regexMatchFields` | String | No | Comma-separated fields to target for regular expression matching. Options differ by `source` (see below). |
| `httpUrl` | String | Yes | Target HTTP destination URL (e.g., `https://my-server.com/endpoint`). |
| `httpMethod` | String | Yes | HTTP verb: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS`. |
| `headers` | Array | No | HTTP headers formatted as list of objects: `[{"header": "Key", "value": "Value"}]`. |
| `bodyTemplate` | String | Yes | Template for request body. Supports substitution variables (see variables table). |
| `_vars` | Array | No | Global variables to inject into the application when imported: `[{"name": "VAR_NAME", "value": "Value"}]`. |

### Regex Match Fields Target Options (`regexMatchFields`)

- **For APP rules:**
  - `title`: Matches only against the Notification Title.
  - `text`: Matches only against the Notification Body text.
  - `both` or `""` (empty): Matches if either title OR text matches.

- **For SMS rules:**
  - `sender`: Matches against the Sender Phone Number.
  - `recipient`: Matches against the local SIM Card phone number.
  - `body`: Matches against the SMS text content.
  - `all` or `""` (empty): Matches only against the SMS text content.

- **For IMAP (Email) rules:**
  - `from`: Matches against the sender email address.
  - `to`: Matches against the target recipient email address.
  - `subject`: Matches against the email subject line.
  - `body`: Matches against the plain-text body content of the email.
  - `all` or `""` (empty): Matches only against the email body content.

---

## Placeholders Reference

You can use the following placeholder variables inside `bodyTemplate` or inside the `value` fields of `headers`:

| Variable | Source | Description |
| :--- | :--- | :--- |
| `{not_title}` | APP / SMS | The notification title (for Apps) or Sender's phone number (for SMS). |
| `{not_text}` | APP / SMS | The notification text (for Apps) or message body (for SMS). |
| `{sms_sender}` | SMS | Sender's originating phone number. |
| `{sms_text}` | SMS | SMS message text content. |
| `{not_type}` | ALL | Event type identifier string (`"notification"`, `"sms"`, or `"imap"`). |
| `{package_name}` | APP | Package ID of the application that spawned the notification. |
| `{device_uuid}` | ALL | Unique persistent hardware/installation UUID of the device. |
| `{timestamp}` | ALL | Timestamp of when the event occurred in milliseconds. |
| `{system_time}` | ALL | System timestamp in milliseconds. |
| `{imap_from}` | IMAP | Email sender (From) address. |
| `{imap_to}` | IMAP | Email recipient (To) address. |
| `{imap_subject}` | IMAP | Email subject header. |
| `{imap_body}` | IMAP | Plain-text content of the fetched email. |
| `{global_VAR}` | ALL | Reads the value of a global variable named `VAR` registered in Settings. |
