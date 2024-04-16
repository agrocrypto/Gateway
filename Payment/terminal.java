package aws.sample.paymentcryptography.terminal;

import java.math.BigInteger;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.amazonaws.util.StringUtils;

import aws.sample.paymentcryptography.ServiceConstants;

/* 
 * Sample class to simulate merchant's payment terminal. This data defined in the DATA_FILE contains DUKTPT keys that are 
 * pre generated from BDK and KSN.
 * This class sends the payment authorization request (similar to what termial does) to the payment service and processes
 * the HMAC response.
 */
public class PaymentTerminal extends AbstractTerminal {
    private static final String KEYS_KSN_DATA_FILE = "/test-data/sample-key-ksn-data.json";

    public static void main(String[] args) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        String paymentAuthorizationUrl = ServiceConstants.HOST
                + ServiceConstants.PAYMENT_PROCESSOR_SERVICE_AUTHORIZE_PAYMENT_API;

        System.out.println("curr dir is " + System.getProperty("user.dir"));
        JSONObject keyAndKSNData = loadKeyAndKSNData();
        JSONArray dataList = keyAndKSNData.getJSONArray("data");

        dataList.forEach(dataObject -> {
            try {

                System.out.println("--------------------------------------------------------------");
                String track2Data = new StringBuilder()
                .append(";")
                .append((new BigInteger(getRandomNumber(20))))
                .append("=")
                .append(new BigInteger(getRandomNumber(4)))
                .append("?")
                .toString();
                
                String encryptedData = encryptData(
                        ((JSONObject) dataObject).get("dataKey").toString(),
                        track2Data,
                        ((JSONObject) dataObject).get("ksn").toString());
                System.out.println("track2data is " + track2Data + ", DUKPT encrypted data is " + encryptedData);
                Thread.sleep(1000);
                StringBuilder builder = new StringBuilder()
                        .append("?encryptedData=")
                        .append(encryptedData)
                        .append("&")
                        .append("ksn=")
                        .append(((JSONObject) dataObject).get("ksn").toString());
                String finalUrl = new StringBuilder(paymentAuthorizationUrl).append(builder).toString();
                ResponseEntity<String> response = restTemplate.getForEntity(finalUrl, String.class);
                System.out.println("Decrypted response from Cryptography Service - " + response.getBody());
                Thread.sleep(1200);
                JSONObject responseObject = new JSONObject(response.getBody());
                //System.out.println("response is " + responseObject.getString("response") + " mac is " + responseObject.getString("mac"));
                System.out.println("HMAC Validated - " + validateHMAC(responseObject.getString("response"),responseObject.getString("mac").toLowerCase()));
                Thread.sleep(3500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static boolean validateHMAC(String response, String macToVerify) throws Exception {
        if (StringUtils.isNullOrEmpty(macToVerify)) {
            return false;
        }
        String hmacOnTerminal = TerminalHMAC.getMac(Hex.encodeHexString(response.getBytes()));
        System.out.println("MAC from payment service - " + macToVerify + ", MAC from terminal - " + hmacOnTerminal);
        return hmacOnTerminal.trim().toLowerCase().startsWith(macToVerify);
    }

    public static String encryptData(String key, String track2Data, String ksn) throws Exception {
        byte[] keyByteArray = Hex.decodeHex(key);
        byte[] key24byte = new byte[24];

        System.arraycopy(keyByteArray, 0, key24byte, 0, 16);
        System.arraycopy(keyByteArray, 0, key24byte, 16, 8);

        Cipher chiper = Cipher.getInstance(TerminalConstants.TRANSFORMATION_WITH_PKCS5_PADDING);
        chiper.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key24byte, TerminalConstants.ALGORITHM),
                new IvParameterSpec(new byte[8]));

        String hexEncocedData = Hex.encodeHexString(track2Data.getBytes("UTF-8"));
        byte[] encVal = chiper.doFinal(Hex.decodeHex(hexEncocedData));
        String encryptedValue = Hex.encodeHexString(encVal);
        return encryptedValue;
    }

    private static JSONObject loadKeyAndKSNData() throws Exception {
        return loadData(System.getProperty("user.dir") + KEYS_KSN_DATA_FILE);
    }

}
