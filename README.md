# Secure Peer-to-Peer Payment System on Android-Based Mobile Devices via Vibration and Acceleration

## Subject Area(s):
- Computer and Information Systems Security
- Cryptographic Algorithms and Protocols
- Mobile Application Development
- Mobile Communication Systems

## Abstract:
The rapid growth of smartphone penetration in the modern era has enabled users to use mobile payment services for everyday transactions. The recent COVID-19 pandemic has significantly increased mobile payment usage and encouraged new account creation and mobile transactions in various peer-to-peer (P2P) mobile payment systems, such as Octopus and mobile wallets. The P2P mobile payment system enables two parties to securely transact by interacting in close physical proximity with mobile devices. The close physical proximity interaction is enabled by the Radio-frequency (RF) technology through Near Field Communication (NFC) and Bluetooth modules embedded in mobile phones. However, RF technology is vulnerable to several cyberattacks, such as eavesdropping and Man-in-the-Middle (MITM) attacks. It highlights the security problems of RF technology and encourages research on alternative solutions for P2P communication. The project proposes a new radio-free P2P communication channel based on vibration for mobile payment systems. The built-in vibrator and accelerometer on the mobile phone are used to send and receive vibration data, ensuring payments are conducted within a physical proximity. Moreover, to fulfill the security goals of the mobile payment system, such as confidentiality, integrity, and availability (CIA) measures, it is essential to integrate cryptographic frameworks for protecting the communication channel during transactions. However, vibration communication channels are low-bandwidth, making them unsuitable for traditional cryptographic protocols. In this study, we propose a lightweight cryptography (LWC) framework that incorporates out-of-band key establishment via a passphrase and PBKDF2, and integrates AES-CTR encryption with HMAC-SHA256 in the communication channel. These LWC protocols aim to ensure the transaction is safe. Finally, functional, Performance, and Penetration testing of the proposed secure vibration communication channel were conducted, which shows that it is feasible in a P2P mobile payment system under certain circumstances. However, the system has demonstrated an alternative, secure, radio-free communication methodology that can potentially be used in further research on P2P communication and lightweight cryptography under a payment system.

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
Android Smartphone users that wish to use the P2P payment system in a fully offline & physical environment.