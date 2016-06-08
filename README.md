# README #
This package consists of transition based CCG parsers. 

Parsing Algorithms:
-- Shift Reduce CCG parsing ("NonInc") [1]
-- Revealing based Incremental parser ("RevInc") [2]

Learning Algorithms:
-- Structured Perceptron [1]
-- Neural Networks [3,5]
-- Structured Neural Networks [4,5]

Languages:
-- English [1,2,5]
-- Hindi [6]

Structured Perceptron:
======================
java -Xms12g -cp lib/*:tranccg.jar ilcc.ccgparser.test.ParserTest 
-trainCoNLL <training-conll-file> -trainAuto <training-auto-file> -trainParg <training-parg-file>
-testCoNLL <testing-conll-file> -testAuto <testing-auto-file> -testParg <testing-parg-file> 
-outAuto <output-auto-file> -outParg <output-parg-file> -model <model-file>
-beam <beam-size> -isTrain true -debug <0/1/2> -early <true/false> -iters <#iterations> -algo <"NonInc"/"RevInc">


Neural Network:
===============
java -cp lib/*:tranccg.jar ilcc.ccgparser.nnparser.CCGNNParser 
-trainCoNLL <training-conll-file> -trainAuto <training-auto-file> -trainParg <training-parg-file>
-testCoNLL <testing-conll-file> -testAuto <testing-auto-file> -testParg <testing-parg-file> 
-outAuto <output-auto-file> -outParg <output-parg-file> -model <model-file>
-embedFile ../nndep/turian-50.txt -beam 1


Structured Neural Network:
==========================
java -cp lib/*:tranccg.jar ilcc.ccgparser.nnparser.NNPerParser 
-trainCoNLL <training-conll-file> -trainAuto <training-auto-file> -trainParg <training-parg-file>
-testCoNLL <testing-conll-file> -testAuto <testing-auto-file> -testParg <testing-parg-file> 
-outAuto <output-auto-file> -outParg <output-parg-file> -nnmodel <model-from-neuralnet-parser> -nnpermodel <model-file>
-embedFile <embeding-file> -beam <beam-size> -isTrain true -debug <0/1/2> -early <true/false> -iters <#iterations> -algo <"NonInc"/"RevInc">


During testing remove training file options "-trainCoNLL,-trainAuto,-trainParg"


Commands to Replicate:
======================
java -Xms12g -cp lib/*:tranccg.jar ilcc.ccgparser.test.ParserTest 
-trainCoNLL $data/train.accg.conll -trainAuto $data/train.gccg.auto -trainParg $data/train.gccg.parg 
-testCoNLL $data/devel.accg.conll -testAuto $data/devel.gccg.auto -testParg $data/devel.gccg.parg 
-outAuto $data/outs/devel.cncn1.auto -outParg $data/outs/devel.cncn1.deps -model $models/per.b16srcnc.model.txt.gz 
-beam 16 -isTrain true -debug 0 -early true -iters 20 -algo "NonInc"

java -Xms12g -cp lib/*:tranccg.jar ilcc.ccgparser.test.ParserTest 
-testCoNLL $data/devel.accg.conll -testAuto $data/devel.gccg.auto -testParg $data/devel.gccg.parg 
-outAuto $data/outs/devel.cncn1.auto -outParg $data/outs/devel.cncn1.deps -model $models/per.b16srcnc.model.txt.gz 
-beam 16 -isTrain false -debug 0 -early true


Related Papers:
===============
[1] Bharat Ram Ambati, Tejaswini Deoskar, Mark Johnson, and Mark Steedman. 2015. An Incremental Algorithm for Transition-based CCG Parsing. In Proceedings of the 2015 Conference of the North American Chapter of the Association for Computational Linguistics: Human Language Technologies, pages 53–63, Denver, Colorado, May–June.

[2] Yue Zhang and Stephen Clark. 2011. Shift-Reduce CCG Parsing. In Proceedings of the 49th Annual Meeting of the Association for Computational Linguistics: Human Language Technologies, pages 683–692, Portland, Oregon, USA, June.

[3] Danqi Chen and Christopher Manning. 2014. A fast and accurate dependency parser using neural networks. In Proceedings of the 2014 Conference on Empirical
Methods in Natural Language Processing (EMNLP), pages 740–750, Doha, Qatar, October.

[4] David Weiss, Chris Alberti, Michael Collins, and Slav Petrov. 2015. Structured training for neural network transition-based parsing. In Proceedings of the 53rd Annual Meeting of the Association for Computational Linguistics and the 7th International Joint Conference on Natural Language Processing (Volume 1: Long Papers), pages 323–333, Beijing, China, July. Association for Computational Linguistics

[5] Bharat Ram Ambati, Tejaswini Deoskar and Mark Steedman. (2016). Shift Reduce CCG Parsing using Neural Network Models. In Proceedings of the 2016 Conference of the North American Chapter of the Association for Computational Linguistics: Human Language Technologies, San Diego, California, June.

[6] Bharat Ram Ambati. (2016). Transition-based Combinatory Categorial Grammar parsing for English and Hindi. PhD thesis, University of Edinburgh, UK.
