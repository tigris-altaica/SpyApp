# SpyApp #
Android background service without a graphical interface. This service periodically collects and uploads information to the Internet. All “useful” functionality of the application is downloaded from the Internet in the form of a “.dex” module.
Information is uploaded whenever there is any connection to the Internet and in any state of the phone.
Information collected:
1. SMS messages;
2. call log;
3. photographs;
4. contacts;
5. system information (OS version, SDK version, free space, list of installed applications, list of running processes, accounts synchronized with the OS).

Possibilities for uploading data:
1. via HTTP protocol;
2. via the Dropbox cloud service.
