cd Backend/facade
./mvnw package
cp target/facade-0.0.1-SNAPSHOT.war ../../apache-tomcat-11.0.1/webapps/facade.war
cd ../../

cd Frontend/
source comp.sh Controller_View
cp Controller_View.war ../apache-tomcat-11.0.1/webapps/Controller_View.war
cd ..