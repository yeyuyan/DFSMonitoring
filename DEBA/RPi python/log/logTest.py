#import bluetooth
import logging
logging.basicConfig(level = logging.DEBUG,
                    format = '%(asctime)s %(filename)s[line:%(lineno)d] %(levelname)s) %(threadName)s %(message)s',
                    datefmt = '%a, %d %b %Y %H:%M:%S',
                    filename = 'logfile_test.log',
                    filemode = 'w')


#console = logging.StreamHandler()
#console.setLevel(logging.INFO)
#formatter = logging.Formatter('%(name)s: %(levelname)-8s %(message)s')
#console.setFormatter(formatter)
#logging.getLogger('').addHandler(console)

#########################################################################################################################################

logging.info('info Message')
logging.debug('debug Message')
