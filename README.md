# EARS
This platform run uploaded java file in Docker container. It take care of creating docker images and builds. It also suports automatic container remave if application run to long. Platofrm also automaticly sort done images into two folders: Succesful and Failed. Image below present steps how plafrom works.

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
