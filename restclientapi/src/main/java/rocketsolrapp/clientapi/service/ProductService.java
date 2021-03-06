package rocketsolrapp.clientapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JacksonJsonParser;
import org.springframework.stereotype.Service;
import rocketsolrapp.clientapi.model.Product;
import rocketsolrapp.clientapi.model.RequestWithParams;
import rocketsolrapp.clientapi.model.SKU;
import rocketsolrapp.clientapi.schema.ProductQueryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final String CORE_NAME = "products";

    @Autowired
    SolrRequester solr;

    @Autowired
    ProductQueryBuilder productRequestbuilder;

    public List<Product> query(RequestWithParams request) {

        final List<Product> result = new ArrayList<>();
        final SolrQuery query = productRequestbuilder.buildProductQuery(request);

        final QueryResponse response = solr.executeQuery(CORE_NAME, query);

        for (SolrDocument solrDocument : response.getResults()) {
            final Product product = new Product();
            product.setId((String) solrDocument.getFieldValue("id"));
            product.setBrand((String) solrDocument.getFieldValue("brand"));
            product.setDepartment((String) solrDocument.getFieldValue("department"));
            product.setDescription((String) solrDocument.getFieldValue("description"));
            product.setPrice((double) solrDocument.getFieldValue("price"));
            product.setTitle((String) solrDocument.getFieldValue("title"));

            for (SolrDocument skuDocument : solrDocument.getChildDocuments()) {
                final SKU sku = new SKU();
                sku.setId((String) skuDocument.getFieldValue("id"));
                sku.setColor((String) skuDocument.getFieldValue("color"));
                sku.setSize((String) skuDocument.getFieldValue("size"));
                product.addSKU(sku);
            }
            result.add(product);
        }
        return result;
    }

    public void add(Product product) throws SolrServerException, IOException {
        final UpdateRequest request = new UpdateRequest();
        request.add(convertToSolrFormat(product), false);
        solr.sendSolrRequest(CORE_NAME, request);
    }

    public void add(List<Product> products) throws SolrServerException, IOException {
        final UpdateRequest request = new UpdateRequest();
        List<SolrInputDocument> inputDocuments = products.stream().map(this::convertToSolrFormat).collect(Collectors.toList());
        request.add(inputDocuments);
        solr.sendSolrRequest(CORE_NAME, request);
    }

    public void clear() throws SolrServerException, IOException {
        final UpdateRequest request = new UpdateRequest();
        request.deleteByQuery("*:*");
        solr.sendSolrRequest(CORE_NAME, request);

    }

    public Product update(Product product) throws SolrServerException, IOException {
        final UpdateRequest request = new UpdateRequest();
        request.add(convertToSolrFormat(product), true);
        solr.sendSolrRequest(CORE_NAME, request);
        return product;
    }

    public void delete(Product product) throws SolrServerException, IOException {
        final UpdateRequest request = new UpdateRequest();
        request.deleteByQuery("_root_:" + product.getId());
        solr.sendSolrRequest(CORE_NAME, request);
    }


    private Map<String, List<SolrDocument>> groupByRoot(SolrDocumentList documents) {
        Map<String, List<SolrDocument>> result = new HashMap<>();
        for (SolrDocument document : documents) {
            final String rootId = (String) document.getFieldValue("_root_");
            if (result.containsKey(rootId)) {
                result.get(rootId).add(document);
            } else {
                result.put(rootId, new ArrayList<>(Collections.singletonList(document)));
            }
        }
        return result;
    }

    private SolrInputDocument convertToSolrFormat(Product product) {
        SolrInputDocument productDocument = new SolrInputDocument();
        productDocument.setField("id", product.getId());
        productDocument.setField("brand", product.getBrand());
        productDocument.setField("price", product.getPrice());
        productDocument.setField("docType", "product");
        productDocument.setField("title", product.getTitle());
        productDocument.setField("description", product.getDescription());
        productDocument.setField("department", product.getDepartment());
        for (SKU sku : product.getSkus()) {
            SolrInputDocument skuDocument = new SolrInputDocument();

            skuDocument.setField("docType", "SKU");
            skuDocument.setField("id", sku.getId());
            skuDocument.setField("color", sku.getColor());
            skuDocument.setField("size", sku.getSize());

            productDocument.addChildDocument(skuDocument);
        }
        return productDocument;
    }

    public void reloadData() throws IOException, SolrServerException{
        clear();
        final byte[] content = Files.readAllBytes(Paths.get("embeddedsolr" + File.separator
                + "src" + File.separator +
                "main" + File.separator +
                "resources" + File.separator +
                "products.json"
        ));
        final ObjectMapper objectMapper = new ObjectMapper();
        final TypeFactory typeFactory = objectMapper.getTypeFactory();
        final List<Product> products = objectMapper.readValue(content, typeFactory.constructCollectionType(List.class, Product.class));
        add(products);
    }
}
