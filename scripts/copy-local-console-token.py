#!/usr/bin/env python3
"""Copy a short-lived local Keycloak access token without printing credentials.

This uses the loopback-only transfer-local-cli direct grant. It is not an
organization OIDC sign-in flow and must not be used against shared deployments.
"""

import getpass
import json
import shutil
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


TOKEN_URL = "http://localhost:8081/realms/transfer-local/protocol/openid-connect/token"


def clipboard_command():
    for name, arguments in (("pbcopy", []), ("wl-copy", []), ("xclip", ["-selection", "clipboard"])):
        if shutil.which(name):
            return [name, *arguments]
    raise RuntimeError("No clipboard command found (pbcopy, wl-copy, or xclip).")


def main():
    username = input("Local Keycloak username: ").strip()
    if not username:
        raise ValueError("A username is required.")
    password = getpass.getpass("Local Keycloak password: ")
    if not password:
        raise ValueError("A password is required.")
    body = urlencode({
        "grant_type": "password",
        "client_id": "transfer-local-cli",
        "username": username,
        "password": password,
    }).encode("utf-8")
    password = None
    request = Request(TOKEN_URL, data=body, headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urlopen(request, timeout=10) as response:
            result = json.load(response)
    except HTTPError as error:
        if error.code in (400, 401):
            try:
                description = json.load(error).get("error_description", "")
            except (ValueError, AttributeError):
                description = ""
            if description == "Account is not fully set up":
                raise RuntimeError("Keycloak says the account is not fully set up. Fill the user's first and last name, clear required actions, and set a non-temporary password.") from None
            if description == "Invalid user credentials":
                raise RuntimeError("Keycloak rejected the username or password. Check the transfer-local user and retry.") from None
            raise RuntimeError("Keycloak rejected the credentials or direct grant; check the user and client setup.") from None
        raise RuntimeError(f"Keycloak returned HTTP {error.code}.") from None
    except URLError:
        raise RuntimeError("Cannot reach local Keycloak at localhost:8081; check the local Compose stack.") from None
    token = result.get("access_token")
    if not isinstance(token, str) or not token:
        raise RuntimeError("Keycloak did not return an access token.")
    subprocess.run(clipboard_command(), input=token.encode("utf-8"),
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    print("Access token copied to the clipboard. Paste it into the console, then clear the clipboard.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Token copy failed: {error}", file=sys.stderr)
        sys.exit(1)
