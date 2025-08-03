Title: Secure Peer-to-Peer Payment System on Android-Based Mobile Devices via Motion Sensors and Haptic Feedback

Subject Area(s):
    - Computer and Information Systems Security
    - Cryptographic Algorithms and Protocols
    - Mobile Application Development
    - Mobile Communication Systems

Abstract:
There have been increasing Peer-to-Peer (P2P) communication applications in Android-based smartphones, such as a mobile payment system via QR-Code or the internet. However, there is a lack of research or application on communication channels or security mechanisms/key sharing based on mobile sensor data.
This project aims to create a secure P2P payment system for Android devices without internet, Bluetooth, or NFC. The system ensures physical proximity-based security by exploiting built-in motion sensors and haptic feedback for cryptographic key exchange & encrypted data transfer. Users generate a shared secret key by movements (e.g., shaking their devices), encrypting payment details, probably using AES-256, and transmitting the transaction via vibration patterns. A practical offline P2P-payment use case demonstrates the system’s viability, guarantees the cryptographic algorithm is secure to prevent different possible attacks, and creates innovative methods for physical P2P communication in future research.

Objectives:
Literature review on the current peer-to-peer payment method and study on the Peer-to-Peer Communication proposed
Study the method to extract the real-time sensor data in Android smartphones and implement a key exchange protocol and encrypted data transfer via accelerometer data and vibration encoding & decoding
Design and develop a mobile application using the proposed approach to achieve an offline peer-to-peer payment system
Evaluate the security, performance, and usability of the proposed approach

Deliverables:
Android Application with motion-based pairing, encrypted haptic communication, and payment interface.

Hardware(s):
Mobile Device: Android Phone
Mobile Device: LG Nexus 5 [Available in CSLab]

Software(s):
API: Android Cryptography
API: Android Haptic
API: Android Sensor
IDE: Android Studio
Language: Kotlin
OS: Android
Version Control: Git

Related Past Project(s):
20CS046: Mobile Peer-to-Peer Payment System Based on Vibration Channel
20CS052: Mobile Peer-to-Peer Payment with Motion-Based Pairing
Liaison with Industry:
-
End Users:
Android Smartphone users that with to use the payment system in a fully offline & physical environment

Remark:
Possible working flow:

    1. Research & Planning
    2. Core Development
    3. UI & Payment Use Case
    4. Integration
    5.Testing & Optimization
    6.Documentation