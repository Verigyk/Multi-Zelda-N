cd Backend/facade
./mvnw package
cp target/facade-0.0.1-SNAPSHOT.war ~/Téléchargements/apache-tomcat-11.0.1/webapps/facade.war
cd ../../

cd Frontend/
source comp.sh Controller_View
cp Controller_View.war ~/Téléchargements/apache-tomcat-11.0.1/webapps/Controller_view.war