package com.intuit.developer.sampleapp.oauth2.controller;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpSession;

import com.intuit.ipp.data.*;
import com.intuit.ipp.data.Error;
import com.intuit.ipp.util.DateUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.developer.sampleapp.oauth2.client.OAuth2PlatformClientFactory;
import com.intuit.ipp.core.Context;
import com.intuit.ipp.core.ServiceType;
import com.intuit.ipp.exception.FMSException;
import com.intuit.ipp.exception.InvalidTokenException;
import com.intuit.ipp.security.OAuth2Authorizer;
import com.intuit.ipp.services.DataService;
import com.intuit.ipp.services.QueryResult;
import com.intuit.ipp.util.Config;
import com.intuit.oauth2.client.OAuth2PlatformClient;
import com.intuit.oauth2.data.BearerTokenResponse;
import com.intuit.oauth2.exception.OAuthException;

@RestController
public class QBOController {

	private static final org.slf4j.Logger LOG = com.intuit.ipp.util.Logger.getLogger();

	@Autowired
	OAuth2PlatformClientFactory factory;

	private static final Logger logger = Logger.getLogger(QBOController.class);

	public Customer getCustomer(DataService service) throws FMSException, ParseException {
		List<Customer> customers = (List<Customer>) service.findAll(new Customer());
		if (!customers.isEmpty()) {
			return customers.get(0);
		}
		//return createCustomer(service);
		return customers.get(0);
	}

//	private static Customer createCustomer(DataService service) throws FMSException, ParseException {
//
//		return service.add(getCustomerWithAllFields());
//	}

	@ResponseBody
    @RequestMapping("/estimate/create")
    public String callQBOCompanyInfo(HttpSession session) {

    	String realmId = (String)session.getAttribute("realmId");
    	if (StringUtils.isEmpty(realmId)) {
    		return new JSONObject().put("response","No realm ID.  QBO calls only work if the accounting scope was passed!").toString();
    	}
    	String accessToken = (String)session.getAttribute("access_token");
    	LOG.info("**********Acces Token "+accessToken);
    	String failureMsg="Failed";
    	String url = factory.getPropertyValue("IntuitAccountingAPIHost") + "/v3/company";
        try {

        	// set custom config
        	Config.setProperty(Config.BASE_URL_QBO, url);

    		//get DataService
    		DataService service = getDataService(realmId, accessToken);

			Estimate estimate = EstimateHelper.getEstimateFields(service);
			Estimate savedEstimate = service.add(estimate);

			if(service.isAvailableAsPDF(savedEstimate)){
/*				try (InputStream fis = service.downloadPDF(savedEstimate);
					 InputStreamReader isr = new InputStreamReader(fis,
							 StandardCharsets.US_ASCII);
					 BufferedReader br = new BufferedReader(isr)) {

					br.lines().forEach(line -> System.out.print(line));
				}*/


				String FILE_TO = "d:\\test.pdf";
				try (InputStream inputStream = service.downloadPDF(savedEstimate)) {

					File file = new File(FILE_TO);
					copyInputStreamToFile(inputStream, file);

				}

				LOG.info("created");
			}else{
				LOG.info("not created");
			}
			return "Estimate created: " + savedEstimate.toString();

/*
			Customer customer = new Customer();

			// Mandatory Fields
			customer.setDisplayName("abc");
			customer.setTitle("mr");
			customer.setGivenName("def");
			customer.setMiddleName("ghi");
			customer.setFamilyName("jkl");

			// Optional Fields
			customer.setCompanyName("ABC Corporations");

			TelephoneNumber mobile = new TelephoneNumber();
				mobile.setFreeFormNumber("(650)111-3333");
				mobile.setDefault(false);
				mobile.setTag("Home");
				customer.setMobile(mobile);

			EmailAddress emailAddr = new EmailAddress();
				emailAddr.setAddress("test@abc.com");
				customer.setPrimaryEmailAddr(emailAddr);

			PhysicalAddress billingAdd = new PhysicalAddress();
				billingAdd.setLine1("123 Main St");
				billingAdd.setCity("Mountain View");
				billingAdd.setCountry("United States");
				billingAdd.setCountrySubDivisionCode("CA");
				billingAdd.setPostalCode("94043");
				customer.setBillAddr(billingAdd);

			PhysicalAddress shipAdd = new PhysicalAddress();
				shipAdd.setLine1("123 Main St");
				shipAdd.setCity("Mountain View");
				shipAdd.setCountry("United States");
				shipAdd.setCountrySubDivisionCode("CA");
				shipAdd.setPostalCode("94043");
				customer.setShipAddr(shipAdd);

				Customer savedCustomer = service.add(customer);
				LOG.info("Customer with mandatory fields created: " + savedCustomer.getId() + " ::customer name: " + savedCustomer.getDisplayName());
				return "Name :"+savedCustomer.getDisplayName()
						+"Billing Address : "+savedCustomer.getBillAddr()
						+"Shipping Address : "+savedCustomer.getShipAddr()
						+"Email : "+savedCustomer.getPrimaryEmailAddr()
						+"Mobile : "+savedCustomer.getMobile();

*/

/*			String sql = "select * from customer where PrimaryEmailAddr = 'test@abc.com'";
			QueryResult queryResult = service.executeQuery(sql);
			Customer customer = (Customer)queryResult.getEntities().get(0);
			LOG.info("Customer name : " + customer.getDisplayName());
			return customer.getDisplayName();*/


		}
	        catch (InvalidTokenException e) {
				logger.error("Error while calling executeQuery :: " + e.getMessage());

				//refresh tokens
	        	logger.info("received 401 during customer info call, refreshing tokens now");
	        	OAuth2PlatformClient client  = factory.getOAuth2PlatformClient();
	        	String refreshToken = (String)session.getAttribute("refresh_token");

				try {
					BearerTokenResponse bearerTokenResponse = client.refreshToken(refreshToken);
					session.setAttribute("access_token", bearerTokenResponse.getAccessToken());
		            session.setAttribute("refresh_token", bearerTokenResponse.getRefreshToken());

		            //call customer info again using new tokens
		            logger.info("calling customer info using new tokens");
		            DataService service = getDataService(realmId, accessToken);

					// get all customer info
					String sql = "select * from customer";
					QueryResult queryResult = service.executeQuery(sql);
					return processResponse(failureMsg, queryResult);

				} catch (OAuthException e1) {
					logger.error("Error while calling bearer token :: " + e.getMessage());
					return new JSONObject().put("response",failureMsg).toString();
				} catch (FMSException e1) {
					logger.error("Error while calling company currency :: " + e.getMessage());
					return new JSONObject().put("response",failureMsg).toString();
				}

			} catch (FMSException e) {
				List<Error> list = e.getErrorList();
				list.forEach(error -> logger.error("Error while calling executeQuery :: " + error.getMessage()));
				return new JSONObject().put("response",failureMsg).toString();
			} catch (Exception e) {
			e.printStackTrace();
			return "Failed";
			}

	}

	private void copyInputStreamToFile(InputStream inputStream, File file)
			throws IOException {

		try (FileOutputStream outputStream = new FileOutputStream(file)) {

			int read;
			byte[] bytes = new byte[1024];

			while ((read = inputStream.read(bytes)) != -1) {
				outputStream.write(bytes, 0, read);
			}

			// commons-io
			//IOUtils.copy(inputStream, outputStream);

		}

	}
	private String processResponse(String failureMsg, QueryResult queryResult) {
//		if (!queryResult.getEntities().isEmpty() && queryResult.getEntities().size() > 0) {
//		//	Customer customer = (Customer) queryResult.getEntities().get(0);
//		//	logger.info("Customer info -> Customer Name: " + customer.getDisplayName());
//			ObjectMapper mapper = new ObjectMapper();
//			try {
//		//		String jsonInString = mapper.writeValueAsString(customer);
//		//		return jsonInString;
//			} catch (JsonProcessingException e) {
//				logger.error("Exception while getting customer info ", e);
//				return new JSONObject().put("response",failureMsg).toString();
//			}
//
//		}
		return failureMsg;
	}

	private DataService getDataService(String realmId, String accessToken) throws FMSException {
		
		//create oauth object
		OAuth2Authorizer oauth = new OAuth2Authorizer(accessToken);
		//create context
		Context context = new Context(oauth, ServiceType.QBO, realmId);

		// create dataservice
		return new DataService(context);
	}

}
