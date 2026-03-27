# Secure Peer-to-Peer Payment System on Android-Based Mobile Devices via Vibration and Acceleration

## Project Code:
25CS023

## Subject Area(s):
- Computer and Information Systems Security
- Cryptographic Algorithms and Protocols
- Mobile Application Development
- Mobile Communication Systems

## Abstract:
The rapid growth of smartphone penetration in the modern era has enabled users to use mobile payment services for 
everyday transactions. The recent COVID-19 pandemic has significantly increased mobile payment usage and encouraged new account 
creation and mobile transactions in various peer-to-peer (P2P) mobile payment systems, such as Octopus and mobile wallets. 
The P2P mobile payment system enables two parties to securely transact by interacting in close physical proximity with 
mobile devices. The close physical proximity interaction is enabled by the Radio-frequency (RF) technology through Near Field 
Communication (NFC) and Bluetooth modules embedded in mobile phones. However, RF technology is vulnerable to several 
cyberattacks, such as eavesdropping and Man-in-the-Middle (MITM) attacks. It highlights the security problems of 
RF technology and encourages research on alternative solutions for P2P communication. The project proposes a 
new radio-free P2P communication channel based on vibration for mobile payment systems. 
The built-in vibrator and accelerometer on the mobile phone are used to send and receive vibration data, 
ensuring payments are conducted within a physical proximity. Moreover, to fulfill the security goals of 
the mobile payment system, such as confidentiality, integrity, and availability (CIA) measures, it is 
essential to integrate cryptographic frameworks for protecting the communication channel during transactions.
However, vibration communication channels are low-bandwidth, making them unsuitable for traditional cryptographic protocols. 
In this study, we propose a lightweight cryptography (LWC) framework that incorporates out-of-band key establishment 
via a passphrase and PBKDF2, and integrates AES-CTR encryption with HMAC-SHA256 in the communication channel. 
These LWC protocols aim to ensure the transaction is safe. Finally, functional, Performance, and Penetration 
testing of the proposed secure vibration communication channel were conducted, which shows that it is 
feasible in a P2P mobile payment system under certain circumstances. However, the system has demonstrated an 
alternative, secure, radio-free communication methodology that can potentially be used in further research on
P2P communication and lightweight cryptography under a payment system.

## Hardware(s):
- **Mobile Device (Sender)**: Samsung Galaxy A8+
- **Mobile Device (Receiver)**: Huawei P30 Pro

## Software(s):
- **API**: Android Cryptography
- **API**: Android Haptic
- **API**: Android Sensor
- **IDE**: Android Studio
- **Language**: Kotlin
- **OS**: Android
- **Version Control**: Git

## End Users:
Android Smartphone users that wish to use the P2P payment system in a fully offline & physical environment

## Key Features:
- Offline peer-to-peer payment over a vibration channel
- Sender can transmit predefined payment transactions through vibration patterns
- Receiver decodes vibration bits using the phone build-in accelerometer to reconstruct payment data
- Optional lightweight cryptographic protection: passphrase-based PSS key establishment, AES-CTR encryption, and truncated HMAC-SHA256 integrity tag verification
- Transaction workflow supports both unprotected mode and protected mode for comparison/testing
- User account login and role-based usage flow

## App Demo (Instructions on installing and running the P2P Payment System):

### Test accounts:
* **Admin:** username: `admin1` / password: `1234`
* **Sender:** username: `test1` / password: `1234`
* **Receiver:** username: `test2`/ password: `1234`

### Instructions on installing
There is many way for installing the application, I will provide it in Wired & Wireless method:
1. Wired method 1 (Copy & Paste method):
    - Connect the Android device to the computer using a USB cable.
    - On your phone, change the USB connection setting to **File Transfer (MTP)** mode.
    - On your computer, open this project folder and locate the APK file at:
        - `app/release/app-release.apk`
    - Copy `app-release.apk` to your Android device (for example, into the `Download` folder).
    - On the Android device, open **Files** app and tap `app-release.apk`.
    - If prompted, enable **Install unknown apps** for Files/Browser, then continue installation.
    - After installation completes, open the app from your app drawer.

   **Note:** If the APK is not found, rebuild the release APK in Android Studio, then check the same path again.

2. Wired method 2 (Using Android Studio):
    - Connect the Android device to the computer using a USB cable.
    - Enable "Developer Options" on the Android device and turn on "USB Debugging."
    - Open Android Studio and open the project folder of the source code and select "Run" to config & install the app on the connected device.

3. Wireless method 1 (Google drive):
    - I have uploaded the application APK file to Google Drive:
        - **Download link:** [https://drive.google.com/drive/folders/17pFGhcP2AiJvYOr5I6hPSMeV6mUkPLB9?usp=drive_link]
    - Open the link on your Android phone and download `app-release.apk`.
    - Wait until the download is completed.
    - Open the downloaded APK file from **Files** app (or tap it directly from browser downloads).
    - If prompted, enable **Install unknown apps** for the app you used to open the APK (Chrome/Drive/Files), then return and continue installation.
    - Tap **Install** and wait for the installation process to finish.
    - Tap **Open** to launch the app, or find it later from the app drawer.
    - Login using one of the test accounts provided in this README.

   **Note:** If installation is blocked, ensure the APK is fully downloaded and that your phone allows installation from unknown sources for the current app.

### How to use the P2P Payment System
1. Prepare two phones:
    - **Sender phone**: login with sender account (e.g., `test1`).
    - **Receiver phone**: login with receiver account (e.g., `test2`).
2. On the receiver phone, open **Receive Transaction** first so it is ready before transmission starts.
3. On the sender phone, open **Send Transaction** and choose the transaction amount (`$100` or `$200`).
4. Select the transaction mode:
    - **Without Protection**: sends the original vibration payload.
    - **With Cryptographic Protection**: enter the passphrase (same on both sides) and send encrypted payload.
5. Keep both phones physically close and stable during vibration transmission until decoding is completed.
6. Check transaction result:
    - If decoding/verification succeeds, receiver gets the transaction data.
    - Sender confirms transaction status in the popup (**Success** or **Fail**) to decide whether balance should be deducted.
7. You can review account balance and payment records from the app screens after each transaction.
