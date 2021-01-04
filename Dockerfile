#-------------------------------
# metaXplor-webapp container
#-------------------------------
# prequisite:
# 	- mongoDB must be launched before (metaxplor-db)
#-------------------------------

FROM tomcat:8.5-alpine

EXPOSE 8080
ARG WEBAPP_IP
ARG WEBAPP_PORT
ARG HPC_IP
ARG HPC_PORT
ARG DB_IP
ARG DB_PORT


#Remove tomcat start-page
RUN rm -rf /usr/local/tomcat/webapps/*


#metaXplor webapp
COPY docker/metaxplor-webapp/setenv.sh /usr/local/tomcat/bin/setenv.sh
COPY target/metaXplor*.war /usr/local/tomcat/webapps/metaXplor.war

RUN mkdir -p webapps/metaXplor \
&& unzip webapps/metaXplor.war -d webapps/metaXplor \
&& rm /usr/local/tomcat/webapps/metaXplor.war

RUN sed -i "s|/path/on/webserver/filesystem|/opt/metaXplor_data/sequences|g" /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/config.properties \
&& sed -i "s|myInstanceName/bank|dockerInstanceBank|g" /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/config.properties \
&& sed -i 's/priority value="DEBUG"/priority value="INFO"/g;s|<appender-ref ref="console" />|<appender-ref ref="console" /> <appender-ref ref="FILE" />|g' webapps/metaXplor/WEB-INF/classes/log4j.xml \
#allowLinking="true" to be able to use symbolic link
&& sed -i "s|<Context path\=\"\/metaXplor\"\/>|<Context path\=\"\/metaXplor\"><Resources allowLinking\=\"true\" \/><\/Context>|g" webapps/metaXplor/META-INF/context.xml \
#Volume for config files
&& mkdir webapp-config \
&& chmod 755 webapp-config \
&& mv /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/config.properties webapp-config \
&& ln -fs /usr/local/tomcat/webapp-config/config.properties /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/config.properties \
&& mv /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/OpalClient.properties webapp-config \
&& ln -fs /usr/local/tomcat/webapp-config/OpalClient.properties /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/OpalClient.properties \
&& mv /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/applicationContext-data.xml webapp-config \
&& ln -fs /usr/local/tomcat/webapp-config/applicationContext-data.xml /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/applicationContext-data.xml \
&& mv /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/datasources.properties webapp-config \
&& ln -fs /usr/local/tomcat/webapp-config/datasources.properties /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/datasources.properties \
&& mv /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/users.properties webapp-config \
&& ln -fs /usr/local/tomcat/webapp-config/users.properties /usr/local/tomcat/webapps/metaXplor/WEB-INF/classes/users.properties


CMD ["catalina.sh","run"]
