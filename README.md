# EARS
This platform run uploaded java file in Docker container. Platform create Docker image, run it and also remove image after its finish. It also suports automatic container remave if application run to long. Platofrm also automaticly sort images into two folders: Succesful and Failed. Platofem supports run of multiple files parrael. Maximum parrarel Docker container can be defind in .env file. Image below present steps how plafrom works.
ENV file:
- set maximum value of running dockers
- set path to folder processor, which must include 3 folders (00ROOT, 01FAILED, 02SUCCESS). Every thing what is in 00ROOT will be copied to Docker container.
- set allowd files (currently supports only .java folder, if Dockerfile is adjust it can run any code)

![alt text](https://github.com/eiifi/EARS/blob/main/EARS%20Flow.jpg?raw=true)

## Requirements

To compile and run these demos you will need:

- JDK 8 or 11+
- GraalVM

See the [Building a Native Executable guide](https://quarkus.io/guides/building-native-image) for help setting up your environment.

## Start the application

The application can be started using: 

```bash
mvn quarkus:dev
```  
