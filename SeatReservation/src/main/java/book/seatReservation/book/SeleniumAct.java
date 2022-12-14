package book.seatReservation.book;

import book.seatReservation.book.model.Pair;
import book.seatReservation.book.model.TodayClass;
import book.seatReservation.config.response.BaseException;
import book.seatReservation.database.seat.model.Seat;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static book.seatReservation.config.response.BaseResponseStatus.*;

@Component
public class SeleniumAct {
    final Logger logger = LoggerFactory.getLogger(this.getClass());
    String chromedriverPath;

    public SeleniumAct() {
        System.setProperty("java.awt.headless", "false");
        WebDriverManager webDriverManager = WebDriverManager.chromedriver();
        webDriverManager.setup();
        this.chromedriverPath = webDriverManager.getDownloadedDriverPath();
        System.out.println("WebDriverManager.getDownloadedDriverPath(): " + this.chromedriverPath);
        System.out.println("WebDriverManager.getDownloadedDriverVersion(): " + webDriverManager.getDownloadedDriverVersion());
    }

    public String refreshPage(WebDriver driver) {
        Duration intervalTime = Duration.ofSeconds(1);
        WebDriverWait interval = new WebDriverWait(driver, intervalTime);
        String class_url = "https://gong.conects.com/gong/academy/classroom";
        driver.get(class_url);
        while (true) {
            try {
                interval.until(ExpectedConditions.alertIsPresent());
                Alert alert = driver.switchTo().alert();
                if (alert != null) {
                    String alertMessage = alert.getText();
                    alert.accept();
                    return alertMessage;
                }
            } catch (NoAlertPresentException noAlert) {
                break;
            } catch (Exception e) {
                break;
            }
        }
        return null;
    }

    public String bookSeat(WebDriver driver, Seat seat, int option) throws BaseException {
        // option: 0) for book 1) for scheduling booking
        if (driver == null)
            throw new BaseException(WEBDRIVER_NOT_FOUND);

        Duration waitTime = Duration.ofSeconds(10);
        WebDriverWait wait = new WebDriverWait(driver, waitTime);
        Duration intervalTime = Duration.ofSeconds(1);
        WebDriverWait interval = new WebDriverWait(driver, intervalTime);

        String refresh = refreshPage(driver);
        if (refresh != null)
            return refresh;

        /** ?????? ????????? ?????? **/
        try {
            String seatMessage = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]"))).getText();
            if (seatMessage.contains("????????? ?????????????????????.")) {
                return "????????? ?????? ?????? ??????????????????.\n" + seatMessage;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new BaseException(SEAT_ALREADY_BOOKED);
        }
        /** ?????? ?????? ?????? ?????? ?????? **/
        try {
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id=\"note_check\"]"))).click(); // ?????? ??????
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new BaseException(CHECK_AGREE_BOX_FAIL);
        }

        /** ?????? ?????? ??? ?????? **/
        long[] dx = {0, 0, 0, 1, 1, 1, -1, -1, -1, 0, 0, 1, 1, -1, -1, 2, 2, 2, 2, 2, -2, -2, -2, -2, -2};
        long[] dy = {0, 1, -1, 0, 1, -1, 0, 1, -1, 2, -2, 2, -2, 2, -2, 0, 1, -1, 2, -2, 0, 1, -1, 2, -2};

        if (seat == null) // ?????? ?????? ?????? ??????
            throw new BaseException(FAILED_TO_LOAD_SEAT);

        String message = "";
        long seat_x = seat.getLongX(); // ?????? ?????? x???
        long seat_y = seat.getY(); // ?????? ?????? y???
        for (int i = 0; i < dx.length; i++) {
            long next_x = seat_x + dx[i];
            long next_y = seat_y + dy[i];

            if (i > 0) { // ?????? ?????? ?????? ??????
                message = String.format("???????????? ??????(%c??? %d??? ?????? ??????)\n", seat.getX(), seat.getY());
            }

            // ????????? ?????? ??????
            try {
                String location = String.format("//*[@id=\"seat_layout_section\"]/div/div[%d]/a[%d]", next_x, next_y);
                interval.until(ExpectedConditions.presenceOfElementLocated(By.xpath(location))).click();
                WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"reserve_button_section\"]/div[2]/a")));
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0].click();", element);

                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]")));
            } catch (UnhandledAlertException e) {
                alertSkipping(driver);
            } catch (TimeoutException e) {
                continue; // ?????? ?????? ??????
            } catch (Exception e) {
                e.printStackTrace();
                throw new BaseException(FAILED_TO_BOOK);
            }
            // ?????? ?????? ??????
            try {
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]")));
            } catch (UnhandledAlertException e) {
                alertSkipping(driver);
            }
            try {
                message += wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]"))).getText();
                if (message.contains("????????? ?????????????????????.")) {
                    return message;
                } else if (message.contains("????????? ?????????????????????.")) {
                    String class_url = "https://gong.conects.com/gong/academy/classroom";
                    driver.get(class_url);
                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]")));
                    message += wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]"))).getText();
                    if (message.contains("????????? ?????????????????????.")) { // ?????? ?????? ?????????
                        return message;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (option == 0)
                break;
        }
        // ?????? ?????? ??????
        throw new BaseException(FAILED_TO_BOOK);
    }

    public String changeSeat(WebDriver driver, Seat seat) throws BaseException {
        if (driver == null)
            throw new BaseException(WEBDRIVER_NOT_FOUND);
        String location = String.format("//*[@id=\"seat_layout_section\"]/div/div[%d]/a[%d]", seat.getLongX(), seat.getY());
        Duration waitTime = Duration.ofSeconds(10);
        WebDriverWait wait = new WebDriverWait(driver, waitTime);

        String refresh = refreshPage(driver);
        if (refresh != null)
            return refresh;

        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(location))).click();

            WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"reserve_button_section\"]/div[2]/a")));
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].click();", element);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]")));
        } catch (UnhandledAlertException e) {
            alertSkipping(driver);
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new BaseException(FAILED_TO_CHANGE_SEAT);
        }

        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]")));
        } catch (UnhandledAlertException e) {
            alertSkipping(driver);
        }

        String message = "?????? ?????? ??????";
        message = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]"))).getText();
//        char x = seat.getX();
//        long y = seat.getY();
//        String expectResult = String.format("%c??? %d??? ????????? ?????????????????????.", x, y);
//
        return message;
    }

    public String cancelSeat(WebDriver driver) throws BaseException {
        if (driver == null)
            throw new BaseException(WEBDRIVER_NOT_FOUND);
        Duration waitTime = Duration.ofSeconds(10);
        WebDriverWait wait = new WebDriverWait(driver, waitTime);

        String refresh = refreshPage(driver);
        if (refresh != null)
            return refresh;

        try {
            // ?????? ?????? ?????? ??????
//        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id=\"reserve_button_section\"]/div[3]/a"))).click();
            WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"reserve_button_section\"]/div[3]/a")));
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].click();", element);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]")));
        } catch (UnhandledAlertException e) {
            alertSkipping(driver);
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new BaseException(FAILED_TO_CANCEL_SEAT);
        }
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]")));
        } catch (UnhandledAlertException e) {
            alertSkipping(driver);
        }

        String result = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]"))).getText();
        String message = "?????? ?????? ?????? ??????";
        if (result.equals("????????? ??????????????????."))
            message = "?????? ?????? ?????? ??????";
        return message;
    }

    public String bookedSeat(WebDriver driver) throws BaseException {
        if (driver == null)
            throw new BaseException(WEBDRIVER_NOT_FOUND);
        Duration waitTime = Duration.ofSeconds(10);
        WebDriverWait wait = new WebDriverWait(driver, waitTime);
        String refresh = refreshPage(driver);
        if (refresh != null)
            return refresh;

        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]")));
        } catch (UnhandledAlertException e) {
            alertSkipping(driver);
        }
        String message = null;
        try {
            message = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]"))).getText();
        } catch (UnhandledAlertException e) {
            alertSkipping(driver);
        } catch (Exception exception) {
            throw new BaseException(FAILED_TO_LOAD_SEAT);
        }
        return message;
    }

    @Deprecated
    public void SeatScreenShot(WebDriver driver) throws BaseException, IOException {
        Duration waitTime = Duration.ofSeconds(10);
        WebDriverWait wait = new WebDriverWait(driver, waitTime);
        Duration intervalTime = Duration.ofNanos(200);
        WebDriverWait interval = new WebDriverWait(driver, intervalTime);
        WebElement ele = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"container\"]/div[3]/div[2]/div/div[2]")));
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        BufferedImage fullImg = ImageIO.read(screenshot);

        Point point = ele.getLocation();
        int eleWidth = ele.getSize().getWidth();
        int eleHeight = ele.getSize().getHeight();
        BufferedImage eleScreenshot = fullImg.getSubimage(point.getX(), point.getY(), 1200, 1000);
        ImageIO.write(eleScreenshot, "png", screenshot);
        File screenshotLocation = new File(String.format("C:\\test\\%s", LocalDate.now() + ".png"));
//        File screenshotLocation = new File(String.format("\\home\\ubuntu\\%s", LocalDate.now()+".png"));
        FileUtils.copyFile(screenshot, screenshotLocation);
    }


    public Pair<WebDriver, String> naverLogin(String id, String pwd) throws BaseException {
        System.setProperty("webdriver.chrome.driver", this.chromedriverPath);
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        options.setCapability(CapabilityType.UNEXPECTED_ALERT_BEHAVIOUR, UnexpectedAlertBehaviour.ACCEPT);
        options.addArguments("start-maximized"); // open Browser in maximized mode
        options.addArguments("disable-gpu"); // applicable to windows os only
        options.addArguments("no-sandbox"); // Bypass OS security model
        options.addArguments("disable-dev-shm-usage"); // overcome limited resource problems
        options.addArguments("lang=ko");
        options.addArguments("headless"); // !headless!

        WebDriver driver = new ChromeDriver(options);
        Duration waitTime = Duration.ofSeconds(10);
        WebDriverWait wait = new WebDriverWait(driver, waitTime);
        Duration intervalTime = Duration.ofSeconds(2);
        WebDriverWait interval = new WebDriverWait(driver, intervalTime);

        try {
            String url = "https://member.conects.com/member/global/login?skin_key=GONG&redirect_url=https://gong.conects.com/gong/main/academy";
            driver.get(url);
            // ????????? ?????????
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"member_login_frm\"]/div/div[1]/ul/li[1]/a"))).click();

            // ????????? ?????????
            try {
                // ?????????????????? ????????? ?????????
                WebElement idBox = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id=\"id\"]")));
                WebElement pwdBox = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id=\"pw\"]")));
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0].value=arguments[1]", idBox, id);
                js.executeScript("arguments[0].value=arguments[1]", pwdBox, pwd);
                wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id=\"log.login\"]"))).click();
            } catch (Exception e) {
                throw new BaseException(FAILED_TO_LOGIN);
            }
        } catch (Exception exception) {
            throw new BaseException(FAILED_TO_LOGIN);
        }
        try {
            wait.until(ExpectedConditions.urlMatches("https://gong.conects.com/gong/main/academy"));
        } catch (Exception exception) {
            throw new BaseException(FAILED_TO_LOGIN);
        }
        String cur_url = driver.getCurrentUrl();
        // ????????? ?????? ??????
        if (!cur_url.equals("https://gong.conects.com/gong/main/academy")) {
            driver.quit();
            throw new BaseException(FAILED_TO_LOGIN);
        } else {
            return new Pair<>(driver, "????????? ??????");
        }
    }

    public void quitDriver(WebDriver webDriver) {
        if (webDriver != null) {
            webDriver.quit();
        }
    }

    public TodayClass getReservedInfo(WebDriver driver) throws BaseException {
        if (driver == null)
            throw new BaseException(WEBDRIVER_NOT_FOUND);
        if (!driver.getCurrentUrl().equals("https://gong.conects.com/gong/academy/classroom"))
            throw new BaseException(FAILED_TO_LOAD_SEAT);
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("#schedule_idx")));
            String doc = driver.getPageSource();
            Document document = Jsoup.parse(doc);
            Elements classes = document.select("#schedule_idx option");
            Elements classrooms = document.select("#info_idx option");
            Elements classInfos = document.selectXpath("//*[@id=\"container\"]/div[3]/div[1]/div/div[2]/ul/li/strong");

            String todayClass = classes.get(0).text().trim().replace("\n", "");
            String todayClassInfo = classrooms.get(0).text().trim().replace("\n", "");
            String todayTime = classInfos.get(0).text().trim().replace("???", "-").replace("???", "-").replace("\n\n", "\n");
            TodayClass retTodayClass = new TodayClass();
            if (todayClass != null)
                retTodayClass.setTodayClass(todayClass);
            if (todayClassInfo != null)
                retTodayClass.setClassLocation(todayClassInfo);
            if (todayTime != null)
                retTodayClass.setClassInfo(todayTime);
            return retTodayClass;
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new BaseException(FAILED_TO_LOAD_TEXT);
        }
    }

    public List<List<Integer>> seatPage(WebDriver driver) throws BaseException {
        if (driver == null)
            throw new BaseException(WEBDRIVER_NOT_FOUND);
        try {
            Duration waitTime = Duration.ofSeconds(10);
            WebDriverWait wait = new WebDriverWait(driver, waitTime);
            try {
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]")));
            } catch (UnhandledAlertException e) {
                alertSkipping(driver);
            }
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id=\"reserve_txt\"]")));
            String doc = driver.getPageSource();
            Document document = Jsoup.parse(doc);
            Elements elements = document.select(".table_group");

            List<List<Integer>> table = new ArrayList<>();
            for (var e : elements) {
                Elements seats = e.getElementsByTag("a");
                List<Integer> t = new ArrayList<>();
                for (var s : seats) {
                    String attr = s.attr("class");
                    Integer integer = null;
                    switch (attr) {
                        case "seat_no":
                        case "seat": // ?????? ????????????
                            integer = 1;
                            break;
                        case "off":  // ?????? ????????? ??????
                            integer = 2;
                            break;
                        case "active": // ??? ??????
                            integer = 3;
                            break;
                        case "way": // ???
                            integer = 4;
                            break;
                        case "block": // ?????? ?????? ??????
                            integer = 5;
                            break;
                    }
                    t.add(integer);
                }
                table.add(t);
            }
            return table;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BaseException(FAILED_TO_LOAD_SEAT);
        }
    }

    private void alertSkipping(WebDriver driver) {
        Duration intervalTime = Duration.ofNanos(500);
        WebDriverWait interval = new WebDriverWait(driver, intervalTime);
        while (true) {
            try {
                interval.until(ExpectedConditions.alertIsPresent());
                Alert alert = driver.switchTo().alert();
                if (alert != null)
                    alert.accept();
            } catch (NoAlertPresentException noAlert) {
                break;
            } catch (TimeoutException timeoutException) {
                break;
            } catch (Exception exception) {
                exception.printStackTrace();
                driver.get(driver.getCurrentUrl());
                break;
            }
        }
    }
}
