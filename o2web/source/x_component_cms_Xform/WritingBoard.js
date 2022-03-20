MWF.xDesktop.requireApp("process.Xform", "WritingBoard", null, false);
MWF.xApplication.cms.Xform.WritingBoard = MWF.CMSWritingBoard =  new Class({
	Extends: MWF.APPWritingBoard,
    upload: function( callback ){
        var img = this.tablet.getImage( null, true );
        if(img)Promise.resolve( img ).then(function( image ){
            Promise.resolve( this.tablet.getFormData(image) ).then(function (formData) {
                o2.xDesktop.uploadImageByScale(
                    this.form.businessData.document.id,
                    "cmsDocument",
                    0, //maxSize
                    formData,
                    image,
                    function (json) {
                        if(callback)callback(json);
                    }.bind(this),
                    function () {

                    }.bind(this)
                );
            }.bind(this))
        }.bind(this))
    },
    validationConfigItem: function(routeName, data){
        var flag = (data.status=="all") ? true: (routeName == "publish");
        if (flag){
            var n = this.getData();
            var v = (data.valueType=="value") ? n : n.length;
            switch (data.operateor){
                case "isnull":
                    if (!v){
                        this.notValidationMode(data.prompt);
                        return false;
                    }
                    break;
                case "notnull":
                    if (v){
                        this.notValidationMode(data.prompt);
                        return false;
                    }
                    break;
                case "gt":
                    if (v>data.value){
                        this.notValidationMode(data.prompt);
                        return false;
                    }
                    break;
                case "lt":
                    if (v<data.value){
                        this.notValidationMode(data.prompt);
                        return false;
                    }
                    break;
                case "equal":
                    if (v==data.value){
                        this.notValidationMode(data.prompt);
                        return false;
                    }
                    break;
                case "neq":
                    if (v!=data.value){
                        this.notValidationMode(data.prompt);
                        return false;
                    }
                    break;
                case "contain":
                    if (v.indexOf(data.value)!=-1){
                        this.notValidationMode(data.prompt);
                        return false;
                    }
                    break;
                case "notcontain":
                    if (v.indexOf(data.value)==-1){
                        this.notValidationMode(data.prompt);
                        return false;
                    }
                    break;
            }
        }
        return true;
    }
	
}); 