package io.codety.scanner.service;

import io.codety.scanner.analyzer.checkov.CheckovCodeAnalyzer;
import io.codety.scanner.analyzer.golangcilint.GolangcilintCodeAnalyzer;
import io.codety.scanner.analyzer.scalastyle.ScalastyleCodeAnalyzer;
import io.codety.scanner.connectivity.CodetyConfigService;
import io.codety.scanner.analyzer.dto.AnalyzerConfigurationDetailDto;
import io.codety.scanner.analyzer.dto.AnalyzerConfigurationDto;
import io.codety.scanner.analyzer.codety.CodetyRegexCodeAnalyzer;
import io.codety.scanner.analyzer.cppcheck.CppcheckCodeAnalyzer;
import io.codety.scanner.analyzer.eslint.EslintCodeAnalyzer;
import io.codety.scanner.analyzer.pylint.PylintCodeAnalyzer;
import io.codety.scanner.analyzer.trivy.TrivyCodeAnalyzer;
import io.codety.scanner.reporter.dto.CodeAnalysisResultDto;
import io.codety.scanner.reporter.dto.CodeAnalysisResultSetDto;
import io.codety.common.dto.CodeAnalyzerType;
import io.codety.scanner.service.dto.AnalyzerRequest;
import io.codety.scanner.reporter.CodeAnalysisResultDistributionService;
import io.codety.scanner.util.CodetyConsoleLogger;
import io.codety.scanner.util.CodetyConstant;
import io.codety.scanner.analyzer.pmd.JavaPmdCodeAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScannerService {
    private static Logger logger = LoggerFactory.getLogger(ScannerService.class);

    @Autowired
    JavaPmdCodeAnalyzer javaPmdCodeAnalyzer;
    @Autowired
    EslintCodeAnalyzer eslintCodeAnalyzer;

    @Autowired
    PylintCodeAnalyzer pylintCodeAnalyzer;

    @Autowired
    CheckovCodeAnalyzer checkovCodeAnalyzer;

    @Autowired
    GolangcilintCodeAnalyzer golangcilintCodeAnalyzer;

    @Autowired
    TrivyCodeAnalyzer trivyCodeAnalyzer;

    @Autowired
    CppcheckCodeAnalyzer cppcheckCodeAnalyzer;

    @Autowired
    ScalastyleCodeAnalyzer scalastyleCodeAnalyzer;

    @Autowired
    CodetyRegexCodeAnalyzer codetyRegexCodeAnalyzer;

    @Autowired
    CodetyConfigService codetyConfigService;


    @Autowired
    CodeAnalysisResultDistributionService codeAnalysisResultDistributionService;
    private static final String INFO_failedToAnalysisResult = "Failed to process the analysis result due to errors. ";
    private static final String INFO_ISSUE_VIA = " issues via ";
    private static final String space = " ";
    public void process(String[] args) {

        //Check whether there's no any argument feeding into the Java application,
        //     Wrong: `java -jar app.jar`
        //     Right: `java -jar app.jar ./`
        if (args == null || args.length == 0) {
            CodetyConsoleLogger.info(CodetyConstant.INFO_INVALID_MAIN_PROCESS_INPUT);
            return;
        }

        AnalyzerRequest request = AnalyzerRequest.processSystemVariablesToRequest(System.getenv(), args);


        AnalyzerConfigurationDto analyzerConfigurationDto = codetyConfigService.downloadRulesetConfig(request);


        CodeAnalysisResultSetDto resultSetDto = null;

        if(analyzerConfigurationDto!=null && analyzerConfigurationDto.getConfigurationDetailDtoList()!=null
        && analyzerConfigurationDto.getConfigurationDetailDtoList().size() > 0
        ) {
            resultSetDto = runCodeAnalysis(analyzerConfigurationDto, request);
        }else{
            resultSetDto = runDefaultCodeAnalysis(request);
        }

        try {
            codeAnalysisResultDistributionService.distributeAnalysisResult(request, resultSetDto);
        } catch (Exception e) {
            CodetyConsoleLogger.info(INFO_failedToAnalysisResult);
            CodetyConsoleLogger.debug(e);
        }finally {
            codetyConfigService.cleanupTmpDirs();
        }

//        System.exit(-1);

    }

    private CodeAnalysisResultSetDto runDefaultCodeAnalysis(AnalyzerRequest request) {
        CodetyConsoleLogger.info("Running default scanner.");
        CodeAnalysisResultSetDto codeAnalysisResultSetDto = new CodeAnalysisResultSetDto();

        List<CodeAnalysisResultDto> codeAnalysisResultDtos = cppcheckCodeAnalyzer.analyzeCode(request);
        codeAnalysisResultSetDto.getCodeAnalysisResultDtoList().addAll(codeAnalysisResultDtos);

        codeAnalysisResultSetDto.getCodeAnalysisResultDtoList().addAll(javaPmdCodeAnalyzer.analyzeCode(request));
        codeAnalysisResultSetDto.getCodeAnalysisResultDtoList().addAll(codetyRegexCodeAnalyzer.analyzeCode(request));

        codeAnalysisResultSetDto.getCodeAnalysisResultDtoList().addAll(eslintCodeAnalyzer.analyzeCode(request));
        codeAnalysisResultSetDto.getCodeAnalysisResultDtoList().addAll(pylintCodeAnalyzer.analyzeCode(request));
        codeAnalysisResultSetDto.getCodeAnalysisResultDtoList().addAll(checkovCodeAnalyzer.analyzeCode(request));
        codeAnalysisResultSetDto.getCodeAnalysisResultDtoList().addAll(golangcilintCodeAnalyzer.analyzeCode(request));
        codeAnalysisResultSetDto.getCodeAnalysisResultDtoList().addAll(scalastyleCodeAnalyzer.analyzeCode(request));

        return codeAnalysisResultSetDto;
    }

    private CodeAnalysisResultSetDto runCodeAnalysis(AnalyzerConfigurationDto analyzerConfigurationDto, AnalyzerRequest request) {
        CodetyConsoleLogger.info("Running scanner by custom code standards.");
        CodeAnalysisResultSetDto resultSetDto = new CodeAnalysisResultSetDto();
        if(analyzerConfigurationDto == null || analyzerConfigurationDto.getConfigurationDetailDtoList() == null){
            return resultSetDto;
        }
        for(AnalyzerConfigurationDetailDto analyzerConfigurationDetailDto : analyzerConfigurationDto.getConfigurationDetailDtoList()){
            List<CodeAnalysisResultDto> codeAnalysisResultDtos = null;
            CodetyConsoleLogger.debug("Start scanning the code via analyzer " + analyzerConfigurationDetailDto.getCodeAnalyzerType().name());
            if(analyzerConfigurationDetailDto.getCodeAnalyzerType() == CodeAnalyzerType.cppcheck){
                codeAnalysisResultDtos = cppcheckCodeAnalyzer.analyzeCode(analyzerConfigurationDetailDto, request);
            }else if(analyzerConfigurationDetailDto.getCodeAnalyzerType() == CodeAnalyzerType.pmd){
                codeAnalysisResultDtos = javaPmdCodeAnalyzer.analyzeCode(analyzerConfigurationDetailDto, request);
            }else if(analyzerConfigurationDetailDto.getCodeAnalyzerType() == CodeAnalyzerType.eslint){
                codeAnalysisResultDtos = eslintCodeAnalyzer.analyzeCode(analyzerConfigurationDetailDto, request);
            }else if(analyzerConfigurationDetailDto.getCodeAnalyzerType() == CodeAnalyzerType.pylint){
                codeAnalysisResultDtos = pylintCodeAnalyzer.analyzeCode(analyzerConfigurationDetailDto, request);
            }
//            else if(analyzerConfigurationDetailDto.getCodeAnalyzerType() == CodeAnalyzerType.trivy){
//                codeAnalysisResultDtos = trivyCodeAnalyzer.analyzeCode(analyzerConfigurationDetailDto, request);
//            }
            else if(analyzerConfigurationDetailDto.getCodeAnalyzerType() == CodeAnalyzerType.codety){
                codeAnalysisResultDtos = codetyRegexCodeAnalyzer.analyzeCode(analyzerConfigurationDetailDto, request);
            }
            if(codeAnalysisResultDtos!=null) {
                CodetyConsoleLogger.debug(codeAnalysisResultDtos.size() + INFO_ISSUE_VIA + analyzerConfigurationDetailDto.getPluginCode() + space + analyzerConfigurationDetailDto.getLanguage());
                resultSetDto.getCodeAnalysisResultDtoList().addAll(codeAnalysisResultDtos);
            }
        }
        return resultSetDto;
    }


}