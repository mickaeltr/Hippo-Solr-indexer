# Hippo Solr indexer

For regularly indexing [Hippo CMS](http://www.onehippo.com/en/products/cms) content into [Solr](http://lucene.apache.org/solr/).

## Build

[Git](http://git-scm.com/) and [Maven](http://maven.apache.org/) (> 2.2) must be installed on your machine.

    git clone git@github.com:mickaeltr/Hippo-Solr-indexer.git
    cd Hippo-Solr-indexer
    mvn install

## Install

To be able to store the indexer configuration in the repository, you will add the **addon-repository** to the **content** module POM:

    <dependency>
      <groupId>org.onehippo.forge.solr.indexer</groupId>
      <artifactId>solr-indexer-addon-repository</artifactId>
      <version>${hippo.solr-indexer.version}</version>
    </dependency>

A Solr indexer webapp must then be deployed in the same container as CMS and site webapps...

### Webapp example

If you want to play with the provided [Webapp example](https://github.com/mickaeltr/Hippo-Solr-indexer/tree/master/webapp-example), create a new Maven module, packaged as WAR, deployed on Cargo, with the following in the POM:

    <dependencies>
      <dependency>
        <groupId>org.onehippo.forge.solr.indexer</groupId>
        <artifactId>solr-indexer-webapp-example</artifactId>
        <version>${hippo.solr-indexer.version}</version>
        <type>war</type>
      </dependency>
    </dependencies>

    <build>
      <finalName>solr</finalName>
    </build>

After you run your project, the Solr application will be available at <http://localhost:8080/solr/>.

### Customized webapp

When you get more serious and want to customize the webapp, you need to mimic the [Webapp example](https://github.com/mickaeltr/Hippo-Solr-indexer/tree/master/webapp-example) in your project. You may then configure:

 * [Spring applicationContext](https://github.com/mickaeltr/Hippo-Solr-indexer/blob/master/webapp-example/src/main/webapp/WEB-INF/applicationContext.xml) (see [Spring documentation for the IoC container](http://static.springsource.org/spring/docs/3.0.x/spring-framework-reference/html/beans.html))
 * [Solr schema]() (see [Solr documentation for SchemaXml](http://wiki.apache.org/solr/SchemaXml))
 * [Solr config](https://github.com/mickaeltr/Hippo-Solr-indexer/blob/master/webapp-example/src/main/webapp/WEB-INF/solr/conf/solrconfig.xml) (see [Solr documentation for SolrConfigXml](http://wiki.apache.org/solr/SolrConfigXml))

## Configure

In order to setup which **document types** and **fields** will be indexed, a configuration node must be created via the CMS console, under **/content/**:

    [solr:configuration]
    - solr:node (string) multiple
    - solr:property (string) multiple

### Nodes

Set up which (inherited) primary types will be indexed.
Example: *hippo:document*

### Properties

Set up which (nested) properties will be indexed.
Example: *ns:title*, *ns:html/hippostd:content*