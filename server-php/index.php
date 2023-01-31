<?php
header( 'content-type: text/html; charset=utf-8' );

error_reporting(E_ALL);
ini_set('display_errors', 1);


$dataString = file_get_contents('php://input');
$data = json_decode($dataString, true);
//var_dump($data);

define ( 'main' , true );


require_once ('includes/global.php');




if (array_key_exists('clientDeviceId', $data) == true)
{
    $clientDeviceId = $data['clientDeviceId'];
}
else {
    echo '{"web":"undefined","result":"missing clientDeviceId"}';
}
if (!array_key_exists('data', $data) == true){
    echo '{"result":"missingData"}';
}


if (array_key_exists('dataKey', $data) == true)
{
    $dataKey = $data['dataKey'];
}
else
{
    $dataKey = -1;
}


$result = $GLOBAL_SQL_Con->query('INSERT INTO `'.$GLOBAL_TBL_STUDY1.'` (`client_device_id`, `data`, `timestamp_server`) VALUES ("'.$clientDeviceId.'", "'.$GLOBAL_SQL_Con->real_escape_string($dataString).'", NOW())');
echo '{"result":'.$result.', "dataKey":'.$dataKey.'}';

?>
