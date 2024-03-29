GridGain Indexing Module
-----------------------------

GridGain indexing module provides capabilities to index cache context and run SQL, full text or
individual field queries against these indexes.

To enable indexing module when starting a standalone node, move 'optional/ignite-indexing' folder to
'libs' folder before running 'ignite.{sh|bat}' script. The content of the module folder will
be added to classpath in this case.

Importing indexing Module In Maven Project
------------------------------------------

If you are using Maven to manage dependencies of your project, you can add indexing module
dependency like this (replace '${ignite.version}' with actual GridGain version you are
interested in):

<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                        http://maven.apache.org/xsd/maven-4.0.0.xsd">
    ...
    <dependencies>
        ...
        <dependency>
            <groupId>org.gridgain</groupId>
            <artifactId>dawn-sql-indexing</artifactId>
            <version>${ignite.version}</version>
        </dependency>
        ...
    </dependencies>
    ...
</project>
