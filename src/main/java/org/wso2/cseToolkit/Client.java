/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.cseToolkit;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.databridge.agent.AgentHolder;
import org.wso2.carbon.databridge.agent.DataPublisher;
import org.wso2.carbon.databridge.commons.Event;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class Client {
    private static Log log = LogFactory.getLog(Client.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yy hh:mm:ss a", Locale.ENGLISH);
    private static final SimpleDateFormat sdfDate = new SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH);
    private static Date date;
    private static String line;
    private static List<Announcements> announcementsArrayList = new ArrayList<>();
    private static List<String> announcementID = new ArrayList<>();
    private static List<Trades> tradesArrayList = new ArrayList<>();
    private static List<String> tradeID = new ArrayList<>();
    private static List<ClosingPrices> closingPricesArrayList = new ArrayList<>();
    private static List<String> closingPriceID = new ArrayList<>();
    private static String lastDate;
    private static String eighthOldestDate;
    private static int count = 1; // TODO: 8/2/16 make this 0. recheck

    public static void main(String[] args) {
        log.info(Arrays.deepToString(args));
        try {
            AgentHolder.setConfigPath(DataPublisherUtil.getDataAgentConfigPath());
            DataPublisherUtil.setTrustStoreParams();
            DataPublisher dataPublisher = new DataPublisher("tcp://localhost:7611", "admin", "admin");
            
            File file = new File("");		
    		String path = FilenameUtils.getFullPathNoEndSeparator(file.getAbsolutePath());
            
            File common = new File(path + File.separator + "TradeFiles");
            fileHandler(common.getCanonicalPath());
            common = new File(path + File.separator + "Announcements"+ File.separator +"Announcements.csv");
            announcementFileReader(common.getCanonicalPath());
            common = new File(path + File.separator + "ClosingPrices");
            closingPriceReader(common.getCanonicalPath());
            sendData(dataPublisher);

        } catch (Throwable e) {
            log.error(e);
        }
    }

    private static void fileHandler(String fileDirectory) throws IOException, ParseException {
        File directory = new File(fileDirectory);
        File[] fList = directory.listFiles(pathname -> !pathname.isHidden());
        List<String> dirNames = new ArrayList<>();
        assert fList != null;
        for (File file : fList) {
            if (file.isDirectory()) {
                dirNames.add(file.getName());
            }
        }
        if (dirNames.size() == 0) {
            log.info("No folders containing trade information found");
        } else {
            Collections.sort(dirNames);
            String dir1Path = fileDirectory + File.separator + dirNames.get(dirNames.size() - 1);
            List<FileNames> namesOfFilesInDir1 = readDirectory(dir1Path);
            orderFiles(namesOfFilesInDir1);
            while (count <= 80 && count <= namesOfFilesInDir1.size()) {
                writeFileContent(dir1Path + File.separator
                        + namesOfFilesInDir1.get(namesOfFilesInDir1.size() - count).actualName);
                count++;
            }
            if (count < 80) {
                if (dirNames.size() > 1) {
                    String dir2Path = fileDirectory + File.separator + dirNames.get(dirNames.size() - 2);
                    List<FileNames> namesOfFilesInDir2 = readDirectory(dir2Path);
                    orderFiles(namesOfFilesInDir2);
                    int i = 1;
                    while (count <= 80 && count <= namesOfFilesInDir2.size()) {
                        writeFileContent(dir2Path + File.separator
                                + namesOfFilesInDir2.get(namesOfFilesInDir2.size() - i).actualName);
                        count++;
                        i++;
                    }
                } else {
                    log.info("Only " + count + " files provided for data processing");
                }
            }
            orderTrades(tradesArrayList);
            if (tradesArrayList.size()>=1){
                Trades lastItem = tradesArrayList.get(tradesArrayList.size() - 1);
                lastDate = lastItem.tradeDate;
                File folders = new File("../Results/ExecutionDetails.csv");
                FileWriter fw = new FileWriter(folders.getCanonicalPath(),true);
                fw.write(new Date()+","+ lastDate+",\n");
                fw.close();
            }

            int countBackwards = tradesArrayList.size()-1;
            int daysCounted = 0;
            String prevDay = "prev";
            while (countBackwards>=0 && daysCounted < 8){
                Trades currTrade = tradesArrayList.get(countBackwards);
                if (!prevDay.equals(currTrade.tradeDate)){
                    prevDay = currTrade.tradeDate;
                    daysCounted++;
                    eighthOldestDate = currTrade.tradeDate;
                }
                --countBackwards;
            }

            if (daysCounted == 8){
                File folders = new File("../PerfectTraderResults/ExecutionDetails.csv");
                FileWriter fw = new FileWriter(folders.getCanonicalPath(),true);
                fw.write(new Date()+","+ eighthOldestDate+",\n");
                fw.close();
            }

            /*if (tradesArrayList.size()>=8){
                Trades eighthOldest = tradesArrayList.get(tradesArrayList.size() - 8);
                eighthOldestDate = eighthOldest.tradeDate;
                File folders = new File("../PerfectTraderResults/ExecutionDetails.csv");
                FileWriter fw = new FileWriter(folders.getCanonicalPath(),true);
                fw.write(new Date()+","+ eighthOldestDate+",\n");
                fw.close();
            }*/
        }
    }

    private static void announcementFileReader(String announcementFilePath) throws IOException, ParseException {
        BufferedReader brAnn = new BufferedReader(new FileReader(announcementFilePath));
        brAnn.readLine(); // first line contain unnecessary data
        while ((line = brAnn.readLine()) != null) {
            List<String> annRow = Arrays.asList(line.split(","));
            String annDate = annRow.get(3).split(" ")[0].replaceAll("\"","");
            if (annDate.equalsIgnoreCase(lastDate)) {
                String annID = annRow.get(0);
                String symbol = annRow.get(1).toUpperCase();
                String shortDesc = annRow.get(2);
                String annTime = annRow.get(3);
                String annType = annRow.get(4);
                date = sdf.parse(annTime);
                //// TODO: 7/15/16 tell them to give time in AM PM format (like in trades file) ----- symbol as JKH, AAF
                //// ---- ID: a unique number per announcement
                long timeInMilliseconds = date.getTime();
                Announcements newAnn = new Announcements(annID, symbol, shortDesc, annTime, annType,
                        timeInMilliseconds);
                if (!announcementID.contains(annID)) {
                    announcementsArrayList.add(newAnn);
                    announcementID.add(annID);
                } else {
                    System.out.println("Duplicate entry for announcement ID " + annID + " found. Discarding duplicate");
                }
            }
        }
        orderAnnouncements(announcementsArrayList);
    }

    private static void closingPriceReader(String closingPricesFilePath) throws ParseException, IOException {
        File dir = new File(closingPricesFilePath);
        File[] fListInDir = dir.listFiles(pathname -> !pathname.isHidden());
        List<ClosingPriceFileNames> namesOfFilesInDir = new ArrayList<>();
        if (fListInDir.length >= 8) {
            for (File file : fListInDir) {
                if (file.isFile()) {
                    String currentFile = file.getName();
                    if (currentFile.startsWith("ClosingPrices_") && currentFile.endsWith(".csv")) {
                        String stringForOrdering = (currentFile.replace("ClosingPrices_", "")).replace(".csv", "");
                        date = sdfDate.parse(stringForOrdering);
                        long timeInMilliseconds = date.getTime();
                        namesOfFilesInDir.add(new ClosingPriceFileNames(currentFile, stringForOrdering, timeInMilliseconds));
                    } else {
                        log.info("Files must follow the naming convention of 'ClosingPrices_<dd>-<MMM>-<yy>.csv'");
                    }
                }
            }
            orderClosingPriceFiles(namesOfFilesInDir);
            for (int i = 1; i < 9; i++) {
                ClosingPriceFileNames fileToRead = namesOfFilesInDir.get(namesOfFilesInDir.size() - i);
                writeClosingPriceFileContent(dir+File.separator+fileToRead.actualName,fileToRead.date.replaceAll("\"",""),fileToRead.timeInMilliseconds);
            }
            orderClosingPrices(closingPricesArrayList);
        }
        else{
            log.info("Closing prices for minimum of 8 days must be provided for executing perfect trader implementation");
        }

        //// TODO: 7/27/16 check size of namesOfFilesInDir >=8
    }

    private static List<FileNames> readDirectory(String dirPath) {
        File dir = new File(dirPath);
        File[] fListInDir = dir.listFiles(pathname -> !pathname.isHidden());
        List<FileNames> namesOfFilesInDir = new ArrayList<>();
        if (fListInDir.length > 0) {
            for (File file : fListInDir) {
                if (file.isFile()) {
                    String currentFile = file.getName();
                    if (currentFile.startsWith("Trade_File") && currentFile.endsWith(".Txt")) {
                        String[] stringForOrdering = (currentFile.replace("Trade_File", "")).replace(".Txt", "")
                                .split("\\.");
                        namesOfFilesInDir.add(new FileNames(currentFile, stringForOrdering[1], stringForOrdering[0]));
                    } else {
                        log.info("Files must follow the naming convention of 'Trade_File <date>.<month>.Txt'");
                    }
                }
            }
        }
        return namesOfFilesInDir;
    }

    private static void writeFileContent(String filePath) throws IOException, ParseException {
        BufferedReader brTrades = new BufferedReader(new FileReader(filePath));
        brTrades.readLine(); // first 2 lines contain unnecessary data
        brTrades.readLine();
        while ((line = brTrades.readLine()) != null) {
            List<String> dataRow = Arrays.asList(line.split("\\s+"));
            if (dataRow.size()==24) {
                String tradeDate = dataRow.get(0).replaceAll("\"","");
                String tranRefNo = tradeDate + "-" + dataRow.get(1);
                String tranNo = dataRow.get(1);
                String security = dataRow.get(2) + "." + dataRow.get(3) + dataRow.get(4);
                int qty = Integer.parseInt(dataRow.get(6));
                double price = Double.parseDouble(dataRow.get(5));
                String tranTime = tradeDate + " " + dataRow.get(20) + " " + dataRow.get(21);
                String buyClient = dataRow.get(9) + dataRow.get(10);
                String buyBroker = dataRow.get(8);
                String sellClient = dataRow.get(13) + dataRow.get(14);
                String sellBroker = dataRow.get(12);

                String lType = dataRow.get(7);

                //// TODO: 7/15/16 remove custodian from stream

                if (lType.equals("N")) {
                    date = sdf.parse(tranTime);
                    long timeInMilliseconds = date.getTime();
                    Trades newTrade = new Trades(tradeDate, tranRefNo, tranNo, security, qty, price, tranTime, buyClient,
                            buyBroker, sellClient, sellBroker, timeInMilliseconds);
                    if (!tradeID.contains(tranRefNo)) {
                        tradesArrayList.add(newTrade);
                        tradeID.add(tranRefNo);
                    } else {
                        log.info("Duplicate entry for tradeID " + tranRefNo + " found. Discarding duplicate");
                    }
                }
                else{
                    log.info("Only N type trades are considered in this implementation. Therefore discarding entry for tradeID "+ tranRefNo);
                }
            } else {
                log.info("Row doesn't contain 24 data points as specified by CSE earlier. Relevant ID: "+dataRow.get(0).replaceAll("\"","")+ "-" + dataRow.get(1));
            }
        }
    }

    private static void writeClosingPriceFileContent(String filePath, String date, long timeInMilliseconds) throws IOException {
        BufferedReader brTrades = new BufferedReader(new FileReader(filePath));
        brTrades.readLine(); // first line contain unnecessary data
        while ((line = brTrades.readLine()) != null) {
            List<String> dataRow = Arrays.asList(line.split(","));
            String nonDigits = dataRow.get(1).replaceAll("\\d","");
            char sub = nonDigits.charAt(nonDigits.length()-1);
            String digits = dataRow.get(1).replaceAll("\\D","");
            while (digits.length()<4){
                digits = "0"+digits;
            }
            String security = nonDigits.substring(0,nonDigits.length()-1)+"."+sub+digits;
            double closingPrice = Double.parseDouble(dataRow.get(2));
            ClosingPrices newClosingPrice = new ClosingPrices(date, security, closingPrice, timeInMilliseconds);
            if (!closingPriceID.contains(date+security)) {
                closingPricesArrayList.add(newClosingPrice);
                closingPriceID.add(date+security);
            } else {
                System.out.println("Duplicate closing price entry for security " + security + " on "+date+" found. Discarding duplicate");
            }
        }
    }

    private static void sendData(DataPublisher dataPublisher) throws InterruptedException {
        String streamId = "LastDay:1.0.0";
        System.out.println("Storing last date (" + lastDate + ") in temporary table");
        Event event1 = new Event(streamId, System.currentTimeMillis(), null, null, new Object[] { lastDate });
        dataPublisher.publish(event1);

        streamId = "Announcements:1.0.0";
        System.out.println("Started sending announcements");

        for (Announcements temp : announcementsArrayList) {
            Event event = new Event(streamId, System.currentTimeMillis(), null, null, new Object[]{temp.annID,
                    temp.symbol, temp.shortDesc, temp.annTime, temp.annType, temp.timeInMilliseconds});
            dataPublisher.publish(event);
        }
        System.out.println("Finished sending announcements");
        Thread.sleep(10000);
        System.out.println(System.currentTimeMillis());
        if (count > 20) {
            streamId = "TradesDuringPastFewMonths:1.0.0";
            System.out.println("Started sending older trading information");
            int itemLocation = 0;
            int countDays = 0;
            String prevDate = null;
            do {
                Trades temp = tradesArrayList.get(itemLocation);
                Event event = new Event(streamId, System.currentTimeMillis(), null, null,
                        new Object[] { temp.tradeDate, temp.tranRefNo, temp.tranNo, temp.security, temp.qty, temp.price,
                                temp.tranTime, temp.buyClient, temp.buyBroker, temp.sellClient, temp.sellBroker,
                                temp.timeInMilliseconds });

                dataPublisher.publish(event);
                itemLocation++;
                if (!Objects.equals(prevDate, temp.tradeDate)) {
                    countDays++;
                    prevDate = temp.tradeDate;
                }
            } while (countDays <= count - 20);
            System.out.println("Finished sending older trading information");
            Thread.sleep(180000);

            streamId = "TradesDuringThisMonth:1.0.0";
            System.out.println("Started sending trading data for this month");
            //for (int i = itemLocation; i < tradesArrayList.size(); i++) {
            for (int i = itemLocation; i < 100000; i++) {
                Trades temp = tradesArrayList.get(i);
                Event event = new Event(streamId, System.currentTimeMillis(), null, null,
                        new Object[] { temp.tradeDate, temp.tranRefNo, temp.tranNo, temp.security, temp.qty, temp.price,
                                temp.tranTime, temp.buyClient, temp.buyBroker, temp.sellClient, temp.sellBroker,
                                temp.timeInMilliseconds });
                dataPublisher.publish(event);
            }
            System.out.println("Finished sending trading data for this month");
        }

        else {
            log.info("Not enough trades provided to use as past trades for Insider Dealing detection");
            streamId = "TradesDuringThisMonth:1.0.0";
            System.out.println("Started sending trading data for this month");
            for (Trades temp : tradesArrayList) {
                Event event = new Event(streamId, System.currentTimeMillis(), null, null,
                        new Object[]{temp.tradeDate, temp.tranRefNo, temp.tranNo, temp.security, temp.qty, temp.price,
                                temp.tranTime, temp.buyClient, temp.buyBroker, temp.sellClient, temp.sellBroker,
                                temp.timeInMilliseconds});
                dataPublisher.publish(event);
            }
            System.out.println("Finished sending trading data for this month");
        }

        Thread.sleep(10000);
        System.out.println("Started sending trading data for " + lastDate);
        streamId = "LastDaysTrades:1.0.0";
        List<Trades> lastDayTrades = tradesArrayList.stream().filter(p -> (p.tradeDate).equalsIgnoreCase(lastDate))
                .collect(Collectors.toList());
        for (Trades x:lastDayTrades) {
            Event event = new Event(streamId, System.currentTimeMillis(), null, null,
                    new Object[] { x.tradeDate, x.tranRefNo, x.tranNo, x.security, x.qty, x.price, x.tranTime, x.buyClient,
                            x.buyBroker, x.sellClient, x.sellBroker, x.timeInMilliseconds });
            dataPublisher.publish(event);
        }
        System.out.println("Finished sending trading data for " + lastDate);
        Thread.sleep(100);
        System.out.println(closingPricesArrayList.size());
        System.out.println(eighthOldestDate);
        System.out.println(closingPricesArrayList.get(0).date);
        System.out.println(eighthOldestDate.equalsIgnoreCase(closingPricesArrayList.get(0).date));
        //Finding perfect trader
        if (closingPricesArrayList.size()>0 && eighthOldestDate!= null && eighthOldestDate.equalsIgnoreCase(closingPricesArrayList.get(0).date)) {
            System.out.println("Started sending closing price data for last 7 trading days");
            List<ClosingPrices> last7DayCP = closingPricesArrayList.stream().filter(p -> !(p.date).equalsIgnoreCase(eighthOldestDate))
                    .collect(Collectors.toList());
            streamId="ClosingPricesOnLastSevenDays:1.0.0";
            for (ClosingPrices cp: last7DayCP) {
                Event event = new Event(streamId,System.currentTimeMillis(),null,null,new Object[]{cp.date,cp.security,cp.closingPrice,cp.timeInMilliseconds});
                dataPublisher.publish(event);
            }
            System.out.println("Finished sending closing price data for last 7 trading days");
            Thread.sleep(60000);

            System.out.println("Started sending closing price data for "+eighthOldestDate);
            List<ClosingPrices> eighthOldestDayCP = closingPricesArrayList.stream().filter(p -> (p.date).equalsIgnoreCase(eighthOldestDate))
                    .collect(Collectors.toList());
            streamId="ClosingPricesOnDayForModellingPT:1.0.0";
            for (ClosingPrices cp: eighthOldestDayCP) {
                Event event = new Event(streamId,System.currentTimeMillis(),null,null,new Object[]{cp.date,cp.security,cp.closingPrice,cp.timeInMilliseconds});
                dataPublisher.publish(event);
            }
            System.out.println("Finished sending closing price data for "+eighthOldestDate);
            Thread.sleep(60000);

            System.out.println("Started sending trading data for "+eighthOldestDate);
            List<Trades> eighthOldestDayTrades = tradesArrayList.stream().filter(p -> (p.tradeDate).equalsIgnoreCase(eighthOldestDate))
                    .collect(Collectors.toList());
            streamId="TradesDuringDayForModellingPT:1.0.0";
            for (Trades x:eighthOldestDayTrades) {
                Event event = new Event(streamId, System.currentTimeMillis(), null, null,
                        new Object[] { x.tradeDate, x.tranRefNo, x.tranNo, x.security, x.qty, x.price, x.tranTime, x.buyClient,
                                x.buyBroker, x.sellClient, x.sellBroker, x.timeInMilliseconds });
                dataPublisher.publish(event);
            }
            System.out.println("Finished sending trading data for "+eighthOldestDate);
            Thread.sleep(120000);

            System.out.println("Send customer information to stream");
            streamId="TriggerCustInfo:1.0.0";
            Event eventA = new Event(streamId, System.currentTimeMillis(), null, null,
                    new Object[] {true});
            dataPublisher.publish(eventA);
            Thread.sleep(60000);

            System.out.println("Send customer summary to stream");
            streamId="TriggerCustSummary:1.0.0";
            Event eventB = new Event(streamId, System.currentTimeMillis(), null, null,
                    new Object[] {true});
            dataPublisher.publish(eventB);
            Thread.sleep(60000);

            //Trigger suspicious client detection based on perfect trader modelling
            System.out.println("Trigger perfect trader detection");
            streamId="FindSuspicious:1.0.0";
            Event eventC = new Event(streamId, System.currentTimeMillis(), null, null,
                    new Object[] {true});
            dataPublisher.publish(eventC);
            Thread.sleep(100);
        }

    }




    private static void orderFiles(final List<FileNames> filesToOrder) {
        Collections.sort(filesToOrder, (o1, o2) -> {

            String x1 = o1.month;
            String x2 = o2.month;
            int sComp = x1.compareTo(x2);
            if (sComp != 0) {
                return sComp;
            }
            String x3 = (o1).day;
            String x4 = (o2).day;
            return x3.compareTo(x4);
        });
    }

    private static void orderTrades(final List<Trades> tradesToOrder) {
        Collections.sort(tradesToOrder, (o1, o2) -> {

            String x1 = String.valueOf((o1).timeInMilliseconds);
            String x2 = String.valueOf((o2).timeInMilliseconds);
            int sComp = x1.compareTo(x2);
            if (sComp != 0) {
                return sComp;
            }
            String x3 = (o1).tranNo;
            String x4 = (o2).tranNo;
            return x3.compareTo(x4);

        });
    }

    private static void orderAnnouncements(final List<Announcements> announcementsToOrder) {
        Collections.sort(announcementsToOrder, (o1, o2) -> {

            String x1 = String.valueOf((o1).timeInMilliseconds);
            String x2 = String.valueOf((o2).timeInMilliseconds);
            return x1.compareTo(x2);
        });
    }

    private static void orderClosingPriceFiles(final List<ClosingPriceFileNames> closingPriceFilesToOrder) {
        Collections.sort(closingPriceFilesToOrder, (o1, o2) -> {

            String x1 = String.valueOf((o1).timeInMilliseconds);
            String x2 = String.valueOf((o2).timeInMilliseconds);
            return x1.compareTo(x2);
        });
    }

    private static void orderClosingPrices(final List<ClosingPrices> closingPricesToOrder) {
        Collections.sort(closingPricesToOrder, (o1, o2) -> {

            String x1 = String.valueOf((o1).timeInMilliseconds);
            String x2 = String.valueOf((o2).timeInMilliseconds);
            return x1.compareTo(x2);
        });
    }

    private static class Trades {
        Trades(String tradeDate, String tranRefNo, String tranNo, String security, int qty, double price,
               String tranTime, String buyClient, String buyBroker, String sellClient, String sellBroker,
               long timeInMilliseconds) {
            this.tradeDate = tradeDate;
            this.tranRefNo = tranRefNo;
            this.tranNo = tranNo;
            this.security = security;
            this.qty = qty;
            this.price = price;
            this.tranTime = tranTime;
            this.buyClient = buyClient;
            this.buyBroker = buyBroker;
            this.sellClient = sellClient;
            this.sellBroker = sellBroker;
            this.timeInMilliseconds = timeInMilliseconds;

        }

        String tradeDate;
        String tranRefNo;
        String tranNo;
        String security;
        int qty;
        double price;
        String tranTime;
        String buyClient;
        String buyBroker;
        String sellClient;
        String sellBroker;
        long timeInMilliseconds;
    }

    private static class Announcements {
        Announcements(String annID, String symbol, String shortDesc, String annTime, String annType,
                long timeInMilliseconds) {
            this.annID = annID;
            this.symbol = symbol;
            this.shortDesc = shortDesc;
            this.annTime = annTime;
            this.annType = annType;
            this.timeInMilliseconds = timeInMilliseconds;
        }

        String annID;
        String symbol;
        String shortDesc;
        String annTime;
        String annType;
        long timeInMilliseconds;
    }

    //// TODO: 7/15/16 inform cse regarding format of announcement file

    private static class ClosingPrices {
        ClosingPrices(String date, String security, double closingPrice, long timeInMilliseconds) {
            this.date = date;
            this.security = security;
            this.closingPrice = closingPrice;
            this.timeInMilliseconds = timeInMilliseconds;
        }
        String date;
        String security;
        double closingPrice;
        long timeInMilliseconds;
    }

    private static class FileNames {
        FileNames(String actualName, String month, String day) {
            this.actualName = actualName;
            this.month = month;
            this.day = day;
        }

        String actualName;
        String month;
        String day;
    }

    private static class ClosingPriceFileNames {
        ClosingPriceFileNames(String actualName, String date, long timeInMilliseconds) {
            this.actualName = actualName;
            this.date = date;
            this.timeInMilliseconds = timeInMilliseconds;
        }

        String actualName;
        String date;
        long timeInMilliseconds;
    }

}
