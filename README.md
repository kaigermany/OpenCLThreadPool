# OpenCLThreadPool
A Java bytecode emulator that trys to run most of the calculations on the GPU


--- This Project is stil in Setup/Build, so it is actually not executable! ---

Status:
 Usable Interface:  5%
 OpenCL Interface: 10%
 Memory-Model:     50%


Unlike the ArpaAPI ( https://github.com/Syncleus/aparapi ) , this Project does not try to translate the given Code into OpenCL's C language, here the Code will be preprocessed and, combined with the Data to process, it will be loaded to the GUP memory there a kerel-function will execute the code and handle the memory processing, too.

